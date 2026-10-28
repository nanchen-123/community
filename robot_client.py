#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
机器人客户端 - 连接后端 API + ROS 仿真控制

后端 API 端点（全部使用 ROBOT_TOKEN 认证，不需要 JWT 登录）:
  GET  /api/robot/tasks?status=pending      拉取待执行任务（拿到即置 running）
  POST /api/robot/report                    上报识别结果（支持 multipart 图片上传）
  POST /api/robot/upload                    单独上传图片，返回 URL
  PUT  /api/robot/tasks/:id/progress        回传巡检进度（progress=100 自动完成）

环境变量:
  API_BASE_URL       http://192.168.160.1:8080   后端地址（Java 后端端口 8080；虚拟机内需指向宿主机 IP）
  ROBOT_TOKEN        robot-dev-token              机器人令牌（与 server/.env 中 ROBOT_TOKEN 一致）
  ROBOT_ID           robot01                      机器人唯一标识
  POLL_INTERVAL      15                           轮询任务间隔(秒)
  TARGET_ROBOT_ID    t5ai-001                     拍照请求目标机器人 ID（T5AI-Board）
"""

import os
import sys
import time
import signal
import logging
import requests
from datetime import datetime
from typing import Optional, List, Dict, Any

import cv2
import numpy as np

import rospy
import actionlib
from move_base_msgs.msg import MoveBaseAction, MoveBaseGoal
from geometry_msgs.msg import Pose, Point, Quaternion
from sensor_msgs.msg import Image
from nav_msgs.msg import Odometry
from cv_bridge import CvBridge
from ultralytics import YOLO

from plate_recognizer import PlateRecognizer
from yolo_detector import MultiObjectDetector

logging.basicConfig(
    level=logging.INFO,
    format="[%(asctime)s] %(levelname)s %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("robot_client")


class RobotAPIClient:
    """后端 API 封装，使用 ROBOT_TOKEN 认证（非 JWT）。"""

    def __init__(self, base_url: str, robot_token: str, robot_id: str):
        self.base_url = base_url.rstrip("/")
        self.robot_token = robot_token
        self.robot_id = robot_id
        self.session = requests.Session()
        # 默认 JSON 请求头
        self.session.headers.update({
            "X-Robot-Token": robot_token,
        })

    def _url(self, path: str) -> str:
        return f"{self.base_url}{path}"

    def _request(self, method, path, **kwargs) -> Optional[dict]:
        """带重试的请求，ROBOT_TOKEN 已在 session headers 中。"""
        url = self._url(path)
        for attempt in range(3):
            try:
                r = self.session.request(method, url, timeout=15, **kwargs)
                if r.status_code == 401:
                    log.error("ROBOT_TOKEN 认证失败，请检查 server/.env 中的 ROBOT_TOKEN")
                    return None
                if not r.ok:
                    log.error("API %s %s -> %d %s", method, path, r.status_code, r.text[:200])
                    return None
                return r.json() if r.content else {}
            except requests.RequestException as e:
                log.error("API %s %s 网络错误: %s (重试 %d/3)", method, path, e, attempt + 1)
                time.sleep(1.5 ** attempt)
        return None

    # ---------- 任务 ----------
    def fetch_pending_tasks(self) -> List[dict]:
        """拉取待执行/进行中任务（status=active → pending+running）。
        后端返回 { total, status, upgraded, data: [...] }，其中：
          * pending 的任务在返回前会被后端自动升为 running（防止多车重复领）
          * running 的任务原样返回（断点续接/重启后恢复）
        """
        r = self._request("GET", "/api/robot/tasks?status=active")
        if r is None:
            return []
        log.info("  拉取任务结果: total=%s upgraded=%s status=%s",
                 r.get("total"), r.get("upgraded", 0), r.get("status"))
        return r.get("data") or []

    def update_progress(self, task_id: str, progress: int,
                        current_waypoint: str = "") -> Optional[dict]:
        """回传进度，progress=100 时后端自动将任务标记为 completed。

        返回值（与以前的 bool 不兼容，调用方必须改造）:
          * 成功：返回后端 JSON（至少包含 cancelled 字段）
          * 失败：返回 None（当作网络错，不视为取消，重试即可）
        """
        progress = max(0, min(100, int(progress)))
        body = {
            "progress": progress,
            "current_waypoint": current_waypoint,
        }
        r = self._request("PUT", f"/api/robot/tasks/{task_id}/progress", json=body)
        return r

    def get_task_status(self, task_id: str) -> Optional[dict]:
        """机器人在阻塞移动过程中主动查询"该任务是否被用户取消了"。

        返回 JSON 至少包含: { status: 'running'|'stopped'|'completed'|..., cancelled: bool }
        失败返回 None（视为未取消，继续移动）。
        """
        r = self._request("GET", f"/api/robot/tasks/{task_id}/status")
        return r

    def report_state(self, current_pose: dict = None, current_waypoint: str = "",
                     battery: float = None, current_task_id: str = None,
                     current_task_name: str = None) -> bool:
        """上报心跳/位姿/当前航点/电量。推荐 5~10s 一次。

        current_pose 为 ROS pose dict: {"position":{"x":...,"y":...,"z":...},
            "orientation":{"x":...,"y":...,"z":...,"w":...}}。
        如果为空也没关系，只要调用接口就会刷新 lastActiveAt → 前端显示在线。
        """
        body = {"robot_id": self.robot_id}
        if current_pose:
            try:
                pos = current_pose.get("position") or {}
                ori = current_pose.get("orientation") or {}
                body["current_pose"] = {
                    "position": {"x": pos.get("x"), "y": pos.get("y"), "z": pos.get("z")},
                    "orientation": {"x": ori.get("x"), "y": ori.get("y"),
                                    "z": ori.get("z"), "w": ori.get("w")},
                }
            except Exception as e:
                log.warning("  current_pose 解析失败: %s", e)
        if current_waypoint:
            body["current_waypoint"] = str(current_waypoint)
        if battery is not None:
            try:
                body["battery"] = float(battery)
            except (TypeError, ValueError):
                pass
        if current_task_id is not None:
            body["current_task_id"] = current_task_id or None
        if current_task_name is not None:
            body["current_task_name"] = current_task_name or None
        r = self._request("PUT", "/api/robot/state", json=body)
        return r is not None

    # ---------- 上报 ----------
    def upload_image(self, img_bgr: np.ndarray) -> Optional[str]:
        """上传图片到后端，返回可访问的 URL。"""
        ok, buf = cv2.imencode(".jpg", img_bgr, [cv2.IMWRITE_JPEG_QUALITY, 85])
        if not ok:
            log.warning("图片编码失败，跳过上传")
            return None
        # 后端 /api/robot/upload 要求 request_id 为必填文本字段，且必须排在 image 之前
        request_id = "robot-%s-%d" % (self.robot_id, int(time.time() * 1000))
        files = {
            "request_id": (None, request_id),
            "image": ("capture.jpg", buf.tobytes(), "image/jpeg"),
        }
        # multipart 请求不能手动设 Content-Type，让 requests 自动生成 boundary
        r = self._request("POST", "/api/robot/upload", files=files)
        if r and r.get("url"):
            return r["url"]
        return None

    def report(self, report_type: str, task_id: str, location: str,
               image_bgr: Optional[np.ndarray] = None,
               extra: Optional[dict] = None) -> bool:
        """
        上报识别结果。

        report_type 必须是后端支持的值:
          - "person" / "行人"       → 写入 hazards 表 (type=人员)
          - "corridor" / "楼道堆积"  → 写入 hazards 表 (type=消防)
          - "plate" / "车辆"        → 写入 plates 表 (需 extra 中带 plate_number)
        """
        # 先上传图片（如果有），拿到 URL
        image_url = None
        if image_bgr is not None:
            image_url = self.upload_image(image_bgr)
            if image_url:
                log.info("  📷 图片已上传: %s", image_url)
            else:
                log.warning("  ⚠️ 图片上传失败，继续上报不带图片")

        body: Dict[str, Any] = {
            "type": report_type,
            "task_id": task_id,
            "location": location,
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }
        if image_url:
            body["image_url"] = image_url
        if extra:
            body.update(extra)

        r = self._request("POST", "/api/robot/report", json=body)
        if r is not None:
            log.info("📤 上报[%s] @%s 成功", report_type, location)
            return True
        return False

    # ---------- 拍照请求 ----------
    def create_photo_request(self, task_id: str, waypoint: str,
                             detect_type: str, target_robot: str,
                             coordinate: Optional[dict] = None) -> Optional[str]:
        """向后台发起拍照请求，让 T5AI-Board 前来拍照识别。

        detect_type: "flame" | "person" | "plate"
        target_robot: T5AI-Board 的 robot_id
        返回 request_id（成功）或 None（失败）。
        """
        body = {
            "task_id": task_id,
            "waypoint": waypoint,
            "detect_type": detect_type,
            "source_robot": self.robot_id,
            "target_robot": target_robot,
        }
        if coordinate:
            body["coordinate"] = coordinate
        r = self._request("POST", "/api/photo/request", json=body)
        if r and r.get("request_id"):
            log.info("📸 拍照请求已创建: %s @%s → %s (request_id=%s)",
                     detect_type, waypoint, target_robot, r["request_id"])
            return r["request_id"]
        return None


class RobotController:
    """ROS 导航 + 三种识别器。"""

    # 默认航点 [name, x, y, z, qx, qy, qz, qw]
    WAYPOINTS = [
        ["Point_1", -0.06206941604614258, 0.014536380767822266, 0.0, 0.0, 0.0, 0.0, 1.0],
        ["Point_2", 3.3250465393066406, -0.06471443176269531, 0.0, 0.0, 0.0, 0.013244259373992668, 0.9999122909503785],
        ["Point_3", 3.3250465393066406, 1.284637451171875, 0.0, 0.0, 0.0, 0.7102342582669244, 0.7039654099343459],
        ["Point_4", 2.415658950805664, 1.284637451171875, 0.0, 0.0, 0.0, -0.6967058342388733, 0.7173569408164359],
        ["Point_5", 2.434537887573242, 1.284637451171875, 0.0, 0.0, 0.0, 0.7006746903004469, 0.7134808885824292],
        ["Point_6", 1.8541224002838135, 1.284637451171875, 0.0, 0.0, 0.0, 0.9999474833810853, 0.010248437921664725],
        ["Point_7", 1.8541224002838135, 3.3652114868164062, 0.0, 0.0, 0.0, 0.7081899822203627, 0.7060219182735918],
        ["Point_8", 0.646591305732727, 3.3652114868164062, 0.0, 0.0, 0.0, 0.9999127411535306, 0.013210226297556873],
        ["Point_9", 0.646591305732727, 2.6503443717956543, 0.0, 0.0, 0.0, 0.9999210797377179, 0.012563212015893725],
        ["Point_10", 0.646591305732727, 0.06968045234680176, 0.0, 0.0, 0.0, 0.9999223074926332, 0.01246511045309506],
        ["Point_11", -0.01285707950592041, 0.06270623207092285, 0.0, 0.0, 0.0, -0.012645382537192192, 0.9999200439537593],
    ]

    # 哪些航点做什么识别
    PERSON_POINTS = {"Point_4", "Point_5"}
    MESS_POINTS = {"Point_6"}
    PLATE_POINTS = {"Point_9"}

    def __init__(self, save_path: str):
        signal.signal(signal.SIGINT, self._sigint)
        signal.signal(signal.SIGTERM, self._sigint)

        rospy.init_node("robot_api_client", anonymous=False)
        self.shutdown_flag = False
        self.navigating = False

        self.bridge = CvBridge()
        self.latest_image: Optional[np.ndarray] = None
        self.save_path = save_path
        os.makedirs(save_path, exist_ok=True)

        rospy.Subscriber("/camera/image_raw", Image, self._image_cb, queue_size=1)

        # move_base 导航
        self.client = actionlib.SimpleActionClient("move_base", MoveBaseAction)
        rospy.loginfo("等待 move_base ...")
        if not self.client.wait_for_server(rospy.Duration(60)):
            raise RuntimeError("move_base 服务器未就绪")
        rospy.loginfo("✅ move_base 已连接")

        # 行人检测 YOLO
        self.yolo_model = YOLO("/home/user01/ros1/src/ceshi1/yolo/best.pt")
        self.resident_cls = 0
        self.outsider_cls = 1

        # Point_6 社区多目标检测（一次推理同时检测堆积物/垃圾/电动车/自行车等）
        self.multi_detector = MultiObjectDetector(
            model_path="/home/user01/ros1/src/ceshi1/yolo/duo.pt",
            save_path=save_path,
            conf=0.3, iou=0.45, imgsz=1280,
            roi=(0.0, 0.0, 0.75, 0.85),
        )

        # 车牌识别
        self.plate_recognizer = PlateRecognizer(save_path=save_path)

    # ---------- 基础 ----------
    def _sigint(self, sig, frame):
        log.warning("中断信号，停止导航...")
        self.shutdown_flag = True
        if self.navigating:
            self.client.cancel_all_goals()

    def _image_cb(self, msg: Image):
        if self.shutdown_flag:
            return
        try:
            self.latest_image = self.bridge.imgmsg_to_cv2(msg, "bgr8")
        except Exception as e:
            log.warning("图像转换失败: %s", e)

    def _go_to(self, waypoint, task_id=None, api=None) -> bool:
        """导航到 waypoint；支持 task_id + api 时，每 1.5s 主动查询后端是否取消。

        返回值：
          True  → 到达目标
          False → 导航失败 / 被取消 / SIGINT
        """
        try:
            if isinstance(waypoint, (list, tuple)):
                name, x, y, z, qx, qy, qz, qw = waypoint
            elif isinstance(waypoint, dict):
                name = waypoint.get("name", "Point")
                x = float(waypoint["x"])
                y = float(waypoint["y"])
                z = float(waypoint.get("z", 0.0))
                qx = float(waypoint.get("qx", 0.0))
                qy = float(waypoint.get("qy", 0.0))
                qz = float(waypoint.get("qz", 0.0))
                qw = float(waypoint.get("qw", 1.0))
            else:
                log.error("waypoint 格式不支持: %s", type(waypoint))
                return False
        except (KeyError, ValueError, TypeError) as e:
            log.error("waypoint 解析失败: %s", e)
            return False

        goal = MoveBaseGoal()
        goal.target_pose.header.frame_id = "map"
        goal.target_pose.header.stamp = rospy.Time.now()
        goal.target_pose.pose = Pose(Point(x, y, z), Quaternion(qx, qy, qz, qw))

        self.navigating = True
        self.client.send_goal(goal)

        # ⚠️ 关键修复：不再 wait_for_result(300s) 死等
        #   改成每 1.5s 醒来一次查三件事：shutdown / action 结束 / 后端是否 stopped 取消
        max_wait_sec = 300.0
        poll_sec = 1.5
        total_waited = 0.0
        arrived = False
        cancelled = False
        last_status_poll = 0.0  # 节流：后端 /status 接口最少 2s 查一次，不要把 3000 端口打爆
        while total_waited < max_wait_sec and not rospy.is_shutdown() and not self.shutdown_flag:
            # SimpleActionClient 的 wait_for_result 支持小超时分段等，返回 True=已结束
            done = self.client.wait_for_result(rospy.Duration(poll_sec))
            total_waited += poll_sec
            if done:
                arrived = (self.client.get_state() == 3)
                break
            # 只有提供了 task_id + api 才查取消（没提供就退化为旧兼容逻辑：只等导航）
            if task_id and api:
                if total_waited - last_status_poll >= 2.0:
                    last_status_poll = total_waited
                    try:
                        st = api.get_task_status(task_id)
                        if st and st.get("cancelled"):
                            log.warning("🛑 后端已标记任务 %s 为 %s，立刻取消导航驻车",
                                        task_id, st.get("status"))
                            cancelled = True
                            break
                    except Exception as _e:
                        log.debug("  查询任务状态失败(忽略): %s", _e)

        # 如果被取消/被 SIGINT，立刻把 move_base 当前正在执行的 goal 也 cancel
        #   (否则用户点停止后，即便代码 break，move_base 内部仍会按原路径继续走到终点)
        if cancelled or self.shutdown_flag:
            try:
                self.client.cancel_goal()
            except Exception as _e:
                log.debug("cancel_goal 失败(忽略): %s", _e)
            # cancel_goal 生效可能需要 100~300ms，小睡一次保证行为停住
            rospy.sleep(0.3)

        self.navigating = False
        if cancelled:
            return False
        return arrived and not self.shutdown_flag

    # ---------- 识别 ----------
    def detect_person(self, point_name: str) -> Optional[dict]:
        if self.latest_image is None:
            return None
        img = self.latest_image.copy()
        results = self.yolo_model(img, conf=0.4, iou=0.45, verbose=False)
        resident, outsider = 0, 0
        h, w = img.shape[:2]
        for r in results:
            if r.boxes is None:
                continue
            for box in r.boxes:
                cls_id = int(box.cls[0].item())
                bx1, by1 = int(box.xyxy[0][0]), int(box.xyxy[0][1])
                bx2, by2 = int(box.xyxy[0][2]), int(box.xyxy[0][3])
                bw, bh = bx2 - bx1, by2 - by1
                if bw < w * 0.03 or bh < h * 0.05:
                    continue
                if cls_id == self.resident_cls:
                    resident += 1
                    c = (0, 255, 0)
                elif cls_id == self.outsider_cls:
                    outsider += 1
                    c = (0, 0, 255)
                else:
                    continue
                cv2.rectangle(img, (bx1, by1), (bx2, by2), c, 3)
                cv2.putText(img, f"{'resident' if cls_id == 0 else 'outsider'} {float(box.conf[0]):.2f}",
                            (bx1, by1 - 8), cv2.FONT_HERSHEY_SIMPLEX, 0.6, c, 2)

        total = resident + outsider
        fname = os.path.join(self.save_path, f"{point_name}_person_{datetime.now():%Y%m%d_%H%M%S}.jpg")
        cv2.imwrite(fname, img)
        if total > 0:
            log.info("📸 行人照片: %s (居民%d, 外来%d)", fname, resident, outsider)
        else:
            log.info("📸 行人照片: %s (无人)", fname)

        return {
            "total_people": total,
            "resident": resident,
            "outsider": outsider,
            "image": img,
        }

    def detect_mess(self, point_name: str) -> Optional[dict]:
        if self.latest_image is None:
            return None
        res = self.multi_detector.detect(self.latest_image, point_name)
        return {
            "has_hazard": res["has_hazard"],
            "hazards": res["hazards"],
            "per_class": res["per_class"],
            "has_mess": res["has_mess"],
            "count": res["corridor_mess"],
            "image": res.get("image", self.latest_image.copy()),
        }

    def detect_plate(self, point_name: str) -> Optional[dict]:
        if self.latest_image is None:
            return None
        plate = self.plate_recognizer.recognize(self.latest_image, point_name)
        return {
            "plate_number": plate or "",
            "image": self.latest_image.copy(),
        }


class MissionRunner:
    """编排 API 客户端 + ROS 控制器，执行单个任务。"""

    def __init__(self, api: RobotAPIClient, robot: RobotController,
                 target_robot_id: str = "t5ai-001"):
        self.api = api
        self.robot = robot
        self.target_robot_id = target_robot_id

    def _normalize_waypoints(self, waypoints):
        """补齐航点坐标：任务里只填了名称、x/y 为空时，按名称回退到本机预设航点。"""
        presets = {}
        for preset in self.robot.WAYPOINTS:
            if isinstance(preset, (list, tuple)) and preset:
                presets[preset[0]] = preset
            elif isinstance(preset, dict):
                presets[preset.get("name")] = preset

        normalized = []
        for wp in waypoints:
            if not isinstance(wp, dict):
                normalized.append(wp)
                continue
            name = wp.get("name")
            if wp.get("x") is None or wp.get("y") is None:
                preset = presets.get(name)
                if preset is None:
                    log.warning("  航点 %s 缺少坐标且无同名预设，跳过", name)
                    continue
                if isinstance(preset, (list, tuple)):
                    merged = dict(wp)
                    merged.update({
                        "x": preset[1], "y": preset[2],
                        "z": preset[3] if len(preset) > 3 else 0.0,
                        "qx": preset[4] if len(preset) > 4 else 0.0,
                        "qy": preset[5] if len(preset) > 5 else 0.0,
                        "qz": preset[6] if len(preset) > 6 else 0.0,
                        "qw": preset[7] if len(preset) > 7 else 1.0,
                    })
                    wp = merged
                else:
                    merged = dict(preset)
                    merged.update({k: v for k, v in wp.items() if v is not None})
                    wp = merged
                log.info("  航点 %s 缺少坐标，已按预设补齐", name)
            normalized.append(wp)
        return normalized

    def run(self, task: dict) -> bool:
        task_id = task.get("id") or task.get("task_id")
        if not task_id:
            log.error("⚠️ 任务缺少 id 字段，跳过: %s", task)
            return False
        task_id = str(task_id)

        waypoints = task.get("waypoints") or self.robot.WAYPOINTS
        if not waypoints:
            waypoints = self.robot.WAYPOINTS
        elif not isinstance(waypoints[0], (list, tuple, dict)):
            log.warning("任务 waypoints 格式不兼容，回退到默认路径")
            waypoints = self.robot.WAYPOINTS
        # 任务里只填了航点名称、未填坐标时，按本机预设航点补齐，避免 float(None) 导致导航失败
        waypoints = self._normalize_waypoints(waypoints)
        if not waypoints:
            log.error("⚠️ 任务 %s 没有可用航点，跳过", task_id)
            return False

        loops = int(task.get("loops") or 1)
        log.info("▶️ 开始执行任务 %s (%d 个航点, %d 圈)", task_id, len(waypoints), loops)

        def _is_cancelled(resp: Optional[dict]) -> bool:
            """从 update_progress / get_task_status 返回里判断取消信号。"""
            return bool(resp and resp.get("cancelled"))

        stopped_by_user = False
        visited = 0  # 成功到达的航点数，用于避免"一个航点都没走到却上报 100%"
        try:
            for loop_idx in range(loops):
                if self.robot.shutdown_flag:
                    break
                log.info("🔁 第 %d/%d 圈开始", loop_idx + 1, loops)

                for i, wp in enumerate(waypoints):
                    if self.robot.shutdown_flag:
                        break

                    name = wp[0] if isinstance(wp, (list, tuple)) else wp.get("name", f"Point_{i+1}")

                    log.info("⏩ [圈%d %d/%d] 前往 %s",
                             loop_idx + 1, i + 1, len(waypoints), name)

                    # ① 传给 _go_to task_id + api，内部每 1.5s 主动轮询取消
                    #    一旦用户在此时点停止，_go_to 会在 1~3s 内 cancel_goal 并 return False
                    ok = self.robot._go_to(wp, task_id=task_id, api=self.api)

                    # ② 再查一次：可能 _go_to 里通过 /status 查到了 cancelled，或进度错漏
                    chk = self.api.get_task_status(task_id)
                    if _is_cancelled(chk):
                        log.warning("🛑 /status 显示任务 %s 已取消 (%s)，立即驻车退出",
                                    task_id, chk.get("status"))
                        stopped_by_user = True
                        break

                    if not ok:
                        log.warning("⚠️ 无法到达 %s，跳过（或已被停止）", name)
                        if stopped_by_user:
                            break
                        continue

                    visited += 1

                    # ③ 到达后按「已完成航点」上报进度，避免出发前就把进度跳到中途
                    progress = int(((loop_idx * len(waypoints) + i + 1) / (loops * len(waypoints))) * 100)
                    resp = self.api.update_progress(task_id, progress, current_waypoint=name)
                    if _is_cancelled(resp):
                        log.warning("🛑 到达 %s 后被告知任务 %s 已停止，终止任务", name, task_id)
                        stopped_by_user = True
                        break
                    if resp is None:
                        log.warning("  进度回传网络失败，继续执行（不视为取消）")
                    log.info("  进度已上报 %d%%", progress)

                    rospy.sleep(3.0)

                    # ④ 识别（到达航点后做：行人/堆积/车牌）
                    #    识别前最后再查一次 cancelled：避免到达点后继续识别浪费时间，
                    #    更重要的是如果 cancelled=True 现在立刻停，后面不会再跑下一个航点
                    chk2 = self.api.get_task_status(task_id)
                    if _is_cancelled(chk2):
                        log.warning("🛑 到达 %s 前用户已停止任务 %s，识别略过并直接退出", name, task_id)
                        stopped_by_user = True
                        break

                    # ④-0 到达航点后，若该航点在任务配置里勾选了「到达自动拍照识别」，
                    #     才执行识别操作（本地 YOLO + T5AI-Board 拍照识别）。
                    #     未勾选则跳过，不做任何识别。
                    auto_photo = isinstance(wp, dict) and wp.get("autoPhoto")
                    detect_type = None
                    if auto_photo:
                        detect_type = wp.get("photoType") or "person"

                    # T5AI-Board 拍照请求 + 本地 YOLO 识别都受 autoPhoto 控制
                    if auto_photo and detect_type:
                        # ① 向后台发起拍照请求，让 T5AI-Board 前来拍照 + AI 识别
                        try:
                            coord = None
                            if isinstance(wp, (list, tuple)) and len(wp) >= 3:
                                coord = {"x": float(wp[1]), "y": float(wp[2])}
                            elif isinstance(wp, dict):
                                coord = {"x": float(wp.get("x", 0)), "y": float(wp.get("y", 0))}
                            self.api.create_photo_request(
                                task_id, name, detect_type,
                                self.target_robot_id, coordinate=coord,
                            )
                        except Exception as e:
                            log.warning("  拍照请求发起失败(不影响本地识别): %s", e)

                        # ② 本地 YOLO 识别：按 photoType 选择对应检测器
                        #    person → 行人（居民/外来人员）
                        #    flame  → 多目标检测（楼道堆积/垃圾/电动车等）
                        #    plate  → 车牌识别
                        if detect_type == "person":
                            res = self.robot.detect_person(name)
                            if res:
                                self.api.report(
                                    "person", task_id, name,
                                    image_bgr=res["image"],
                                    extra={
                                        "resident": res["resident"],
                                        "outsider": res["outsider"],
                                        "total": res["total_people"],
                                    },
                                )
                        elif detect_type == "flame":
                            res = self.robot.detect_mess(name)
                            if res:
                                per_class = res["per_class"]
                                note_parts = []
                                if per_class.get("debris", 0):
                                    note_parts.append(f"堆积物{per_class['debris']}")
                                if per_class.get("trash", 0):
                                    note_parts.append(f"垃圾{per_class['trash']}")
                                if per_class.get("e-bike", 0):
                                    note_parts.append(f"电动车乱停{per_class['e-bike']}")
                                if per_class.get("bicycle", 0):
                                    note_parts.append(f"自行车乱停{per_class['bicycle']}")

                                self.api.report(
                                    "corridor", task_id, name,
                                    image_bgr=res["image"],
                                    extra={
                                        "has_hazard": res["has_hazard"],
                                        "total_hazards": res["hazards"],
                                        "per_class": per_class,
                                        "note": "、".join(note_parts) if note_parts else "环境整洁",
                                        "has_mess": res["has_mess"],
                                        "count": res["count"],
                                    },
                                )
                        elif detect_type == "plate":
                            res = self.robot.detect_plate(name)
                            if res and res["plate_number"]:
                                self.api.report(
                                    "plate", task_id, name,
                                    image_bgr=res["image"],
                                    extra={"plate_number": res["plate_number"]},
                                )

                    rospy.sleep(2.0)

                if stopped_by_user or self.robot.shutdown_flag:
                    break

            # 任务结束：如果是正常完成 → progress=100 后端自动 completed；
            #          如果是 stopped_by_user → 不要再发 100（会把 stopped 覆盖回 completed）
            if not stopped_by_user and not self.robot.shutdown_flag:
                # 一个航点都没成功到达时不能上报 100%，否则任务会被误标为已完成
                if visited == 0:
                    log.error("❌ 任务 %s 没有任何航点导航成功，保持「执行中」，请检查航点坐标与 move_base 状态", task_id)
                    return False
                self.api.update_progress(task_id, 100, current_waypoint="DONE")
                log.info("✅ 任务 %s 完成", task_id)
                return True
            return False
        finally:
            # 退出任务时的兜底：把 move_base 当前 goal 取消，确保"停止后小车真的不动"
            #   —— 否则如果上一轮 _go_to 刚好 send_goal 完还没查 cancelled，用户停止时
            #      可能不会被 cancel_goal 覆盖，车仍会继续走
            try:
                self.robot.client.cancel_all_goals()
            except Exception as _e:
                log.debug("  任务退出时 cancel_all_goals(忽略): %s", _e)
            if stopped_by_user:
                log.info("🛑 任务 %s 已按用户停止指令驻车退出", task_id)

    @staticmethod
    def _format_wp(wp: dict):
        return [
            wp.get("name", "Point"),
            wp["x"], wp["y"], wp.get("z", 0.0),
            wp.get("qx", 0), wp.get("qy", 0),
            wp.get("qz", 0), wp.get("qw", 1),
        ]


def main():
    api_base = os.environ.get("API_BASE_URL", "http://192.168.160.1:8080")
    robot_token = os.environ.get("ROBOT_TOKEN", "robot-dev-token")
    robot_id = os.environ.get("ROBOT_ID", "robot01")
    poll_interval = int(os.environ.get("POLL_INTERVAL", "15"))
    save_path = os.environ.get("SAVE_PATH", os.path.expanduser("~/ros1/src/ceshi1/pic"))
    # T5AI-Board 的 robot_id：ROS 小车到达识别航点时向后台发起拍照请求，指定此机器人为拍照执行者
    target_robot_id = os.environ.get("TARGET_ROBOT_ID", "t5ai-001")
    log.info("=" * 60)
    log.info("🤖 机器人客户端启动")
    log.info("  API:         %s", api_base)
    log.info("  Token:       %s", robot_token)
    log.info("  RobotID:     %s", robot_id)
    log.info("  Poll:        %ds", poll_interval)
    log.info("  Save:        %s", save_path)
    log.info("  TargetRobot: %s (拍照请求目标)", target_robot_id)
    log.info("=" * 60)

    api = RobotAPIClient(api_base, robot_token, robot_id)
    robot = RobotController(save_path)
    runner = MissionRunner(api, robot, target_robot_id=target_robot_id)

    # ---------- 心跳线程：每 8s 上报一次 state（前端显示"在线"）----------
    import threading
    heartbeat_stop = threading.Event()
    current_ref = {"task_id": None, "task_name": None, "waypoint": ""}

    def _heartbeat_loop():
        """后台心跳：始终运行，保证前端机器人状态显示为在线/已连接。"""
        first = True
        while not heartbeat_stop.is_set():
            try:
                # 从 /odom 获取当前位姿（如果可用）
                pose = None
                try:
                    pose_msg = rospy.wait_for_message("/odom", Odometry, timeout=1.0)
                    pos = pose_msg.pose.pose.position
                    ori = pose_msg.pose.pose.orientation
                    pose = {
                        "position": {"x": pos.x, "y": pos.y, "z": pos.z},
                        "orientation": {"x": ori.x, "y": ori.y, "z": ori.z, "w": ori.w},
                    }
                except Exception:
                    pass
                ok = api.report_state(
                    current_pose=pose,
                    current_waypoint=current_ref["waypoint"] or None,
                    current_task_id=current_ref["task_id"],
                    current_task_name=current_ref["task_name"],
                    battery=None,
                )
                if first:
                    if ok:
                        log.info("💓 心跳线程已启动，首帧上报完成")
                    else:
                        log.warning("⚠️ 心跳首帧上报失败，请检查 API_BASE_URL 与 ROBOT_TOKEN")
                    first = False
            except Exception as e:
                log.debug("  心跳上报失败(非致命): %s", e)
            # 间隔 8s < ONLINE_TTL_MS=30s 预留充足余量
            for _ in range(80):
                if heartbeat_stop.is_set():
                    break
                time.sleep(0.1)

    hb_thread = threading.Thread(target=_heartbeat_loop, name="robot-heartbeat", daemon=True)
    hb_thread.start()

    # 在 MissionRunner 执行每个航点前后把引用打进来，这样心跳线程能带上"当前航点/当前任务"
    _orig_run = runner.run

    def _patched_run(task):
        tid = task.get("id")
        current_ref["task_id"] = tid
        current_ref["task_name"] = task.get("name")
        current_ref["waypoint"] = ""
        try:
            # 补丁 _go_to：每次导航时刷新 current_ref["waypoint"]
            orig_go_to = robot._go_to

            def _patched_go_to(waypoint, *args, **kwargs):
                if isinstance(waypoint, (list, tuple)):
                    current_ref["waypoint"] = str(waypoint[0])
                elif isinstance(waypoint, dict):
                    current_ref["waypoint"] = waypoint.get("name", "")
                return orig_go_to(waypoint, *args, **kwargs)
            robot._go_to = _patched_go_to
            return _orig_run(task)
        finally:
            robot._go_to = orig_go_to
            current_ref["task_id"] = None
            current_ref["task_name"] = None
            current_ref["waypoint"] = ""
    runner.run = _patched_run

    log.info("🎯 进入任务轮询循环 ...")
    while not robot.shutdown_flag:
        try:
            tasks = api.fetch_pending_tasks()
            if not tasks:
                log.info("⏳ 暂无待执行任务，%ds 后重试", poll_interval)
                time.sleep(poll_interval)
                continue

            task = tasks[0]
            log.info("📥 领取任务: %s", task.get("id"))
            try:
                ok = runner.run(task)
                if not ok:
                    # 任务未完成（被取消/导航全失败），稍作等待再拉取，避免同一任务被立刻反复取出
                    log.info("任务 %s 未完成，%ds 后重新拉取", task.get("id"), poll_interval)
                    time.sleep(poll_interval)
            except Exception as e:
                log.exception("任务执行异常: %s", e)
                time.sleep(poll_interval)

        except Exception as e:
            log.exception("轮询异常: %s", e)
            time.sleep(poll_interval)

    heartbeat_stop.set()
    log.info("👋 机器人客户端已退出")


if __name__ == "__main__":
    try:
        main()
    except rospy.ROSInterruptException:
        log.info("ROS 中断")
    except KeyboardInterrupt:
        log.info("用户中断")
    except Exception as e:
        log.exception("致命错误: %s", e)
        sys.exit(1)
