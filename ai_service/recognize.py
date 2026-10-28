"""
本机 AI 识别服务（Flask）
- 行人模型 best.pt（resident/outsider）+ 多目标模型 duo.pt（堆积物/垃圾/电动车等）
- 复用 PaddleOCR（车牌识别）
- 独立 HTTP 服务，端口 5000
- 后端 Node.js 通过 HTTP 调用本服务

接口：
  POST /recognize
    Body: {"image_path": "uploads/xxx.jpg", "detect_type": "flame|person|plate"}
    返回: {"objects": [...], "plates": [...], "annotated_path": "..."}

运行：
  python recognize.py
"""
import os
import sys
import json
import re
import time
import traceback
from datetime import datetime

from flask import Flask, request, jsonify
from flask_cors import CORS
import cv2
import numpy as np

# 禁用 oneDNN：PaddlePaddle 3.x + oneDNN 在 PIR 执行器下有 ArrayAttribute 兼容性问题
os.environ.setdefault('FLAGS_use_mkldnn', '0')
os.environ.setdefault('FLAGS_enable_pir_in_executor', '0')
os.environ.setdefault('PADDLE_PDX_ENABLE_MKLDNN_BYDEFAULT', 'False')

# ========== 配置 ==========
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(BASE_DIR, 'models')
OUTPUT_DIR = os.path.join(BASE_DIR, 'outputs')          # T5AI-Board 板子拍摄的标注图
OUTPUT_LOCAL_DIR = os.path.join(BASE_DIR, 'outputs_local')  # robot_client 本地摄像头的标注图
# Java 后端上传目录（upload.dir 默认 backend/uploads）
UPLOAD_BASE = os.environ.get('AI_UPLOAD_BASE', os.path.join(BASE_DIR, '..', 'backend', 'uploads'))
os.makedirs(MODEL_DIR, exist_ok=True)
os.makedirs(OUTPUT_DIR, exist_ok=True)
os.makedirs(OUTPUT_LOCAL_DIR, exist_ok=True)

YOLO_MODEL_PATH = os.path.join(MODEL_DIR, 'best.pt')   # 行人模型：resident / outsider
DUO_MODEL_PATH = os.path.join(MODEL_DIR, 'duo.pt')     # 多目标模型：堆积物/垃圾/电动车等

# YOLO 类别映射（与 ROS 端一致）
# best.pt: cls 0 = resident（居民）, cls 1 = outsider（外来人员）
PERSON_CLASSES = {0: 'resident', 1: 'outsider'}
# duo.pt: 多目标检测关注的隐患类别（堆积物/垃圾/电动车/自行车/车辆）
MULTI_CLASSES = {'debris', 'trash', 'e-bike', 'bicycle', 'car'}

# 中国车牌形态：1 位汉字（OCR 可能漏检，故可缺省）+ 5~7 位字母数字
PLATE_PATTERN = re.compile(r'^[\u4e00-\u9fa5]?[A-Z0-9]{5,7}$')

app = Flask(__name__)
CORS(app)

# ========== 模型加载（惰性，失败则进入 mock 模式）==========
yolo_model = None
duo_model = None
ocr_engine = None
mock_mode = False  # 模型不可用时返回模拟数据

def load_yolo():
    global yolo_model, mock_mode
    if yolo_model is not None:
        return True
    try:
        from ultralytics import YOLO
        if os.path.exists(YOLO_MODEL_PATH):
            yolo_model = YOLO(YOLO_MODEL_PATH)
            print(f"[AI] YOLO 模型加载成功: {YOLO_MODEL_PATH}")
            return True
        else:
            print(f"[AI] ⚠️ YOLO 模型文件不存在: {YOLO_MODEL_PATH}")
            print(f"[AI]    请从虚拟机拷贝 best.pt 到 {MODEL_DIR}/")
            mock_mode = True
            return False
    except ImportError:
        print("[AI] ⚠️ ultralytics 未安装，YOLO 不可用。运行: pip install ultralytics")
        mock_mode = True
        return False
    except Exception as e:
        print(f"[AI] ⚠️ YOLO 加载失败: {e}")
        mock_mode = True
        return False

def load_duo():
    """加载多目标模型 duo.pt（堆积物/垃圾/电动车/自行车/车辆等）"""
    global duo_model, mock_mode
    if duo_model is not None:
        return True
    try:
        from ultralytics import YOLO
        if os.path.exists(DUO_MODEL_PATH):
            duo_model = YOLO(DUO_MODEL_PATH)
            print(f"[AI] 多目标模型加载成功: {DUO_MODEL_PATH}")
            return True
        else:
            print(f"[AI] ⚠️ 多目标模型文件不存在: {DUO_MODEL_PATH}")
            mock_mode = True
            return False
    except ImportError:
        print("[AI] ⚠️ ultralytics 未安装，多目标模型不可用。运行: pip install ultralytics")
        mock_mode = True
        return False
    except Exception as e:
        print(f"[AI] ⚠️ 多目标模型加载失败: {e}")
        mock_mode = True
        return False

def load_ocr():
    global ocr_engine, mock_mode
    if ocr_engine is not None:
        return True
    try:
        import paddle
        try:
            paddle.set_flags({'FLAGS_use_mkldnn': False, 'FLAGS_enable_pir_in_executor': False})
        except Exception:
            pass
        from paddleocr import PaddleOCR
        # PaddleOCR 3.x: use_angle_cls 已弃用，改用 use_textline_orientation
        try:
            ocr_engine = PaddleOCR(use_textline_orientation=True, lang='ch')
        except TypeError:
            # PaddleOCR 2.x 兼容（Python 3.8 环境通常只能装 2.x）
            ocr_engine = PaddleOCR(use_angle_cls=True, lang='ch')
        print("[AI] PaddleOCR 加载成功")
        return True
    except ImportError:
        print("[AI] ⚠️ paddleocr 未安装，OCR 不可用。运行: pip install paddleocr")
        mock_mode = True
        return False
    except Exception as e:
        print(f"[AI] ⚠️ PaddleOCR 加载失败: {e}")
        mock_mode = True
        return False

# ========== 识别核心 ==========

def detect_person(img):
    """行人识别（居民/外来人员）"""
    objects = []
    if not load_yolo():
        return objects  # mock 模式下由上层处理

    results = yolo_model(img, conf=0.4, iou=0.45, verbose=False)
    h, w = img.shape[:2]
    for r in results:
        if r.boxes is None:
            continue
        for box in r.boxes:
            cls_id = int(box.cls[0].item())
            if cls_id not in PERSON_CLASSES:
                continue
            bx1, by1 = int(box.xyxy[0][0]), int(box.xyxy[0][1])
            bx2, by2 = int(box.xyxy[0][2]), int(box.xyxy[0][3])
            bw, bh = bx2 - bx1, by2 - by1
            if bw < w * 0.03 or bh < h * 0.05:
                continue
            objects.append({
                'label': PERSON_CLASSES[cls_id],
                'confidence': round(float(box.conf[0]), 4),
                'bbox': [bx1, by1, bx2, by2]
            })
            color = (0, 255, 0) if cls_id == 0 else (0, 0, 255)
            cv2.rectangle(img, (bx1, by1), (bx2, by2), color, 3)
            cv2.putText(img, f"{PERSON_CLASSES[cls_id]} {float(box.conf[0]):.2f}",
                        (bx1, by1 - 8), cv2.FONT_HERSHEY_SIMPLEX, 0.6, color, 2)
    return objects

def detect_multi(img):
    """多目标检测（duo.pt：堆积物/垃圾/电动车/自行车/车辆等社区隐患）"""
    objects = []
    if not load_duo():
        return objects

    # 板子照片分辨率较低（约 320×240），duo.pt 置信度普遍偏低，故阈值取 0.15
    results = duo_model(img, conf=0.15, iou=0.45, verbose=False)
    for r in results:
        if r.boxes is None:
            continue
        for box in r.boxes:
            cls_name = r.names.get(int(box.cls[0].item()), '')
            if cls_name not in MULTI_CLASSES:
                continue
            bx1, by1 = int(box.xyxy[0][0]), int(box.xyxy[0][1])
            bx2, by2 = int(box.xyxy[0][2]), int(box.xyxy[0][3])
            objects.append({
                'label': cls_name,
                'confidence': round(float(box.conf[0]), 4),
                'bbox': [bx1, by1, bx2, by2]
            })
            cv2.rectangle(img, (bx1, by1), (bx2, by2), (0, 140, 255), 3)
            cv2.putText(img, f"{cls_name} {float(box.conf[0]):.2f}",
                        (bx1, by1 - 8), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 140, 255), 2)
    return objects

def detect_plate(img):
    """车牌 OCR 识别"""
    plates = []
    if not load_ocr():
        return plates

    result = ocr_engine.ocr(img)
    if not result or not result[0]:
        return plates

    ocr_res = result[0]

    # 兼容 PaddleOCR 2.x / 3.x 两种返回格式
    if isinstance(ocr_res, dict):
        # PaddleOCR 3.x: OCRResult dict-like，键：rec_texts / rec_scores / dt_polys
        texts = ocr_res.get('rec_texts') or []
        scores = ocr_res.get('rec_scores') or []
        polys = ocr_res.get('rec_polys') or ocr_res.get('dt_polys') or []
    else:
        # PaddleOCR 2.x: list of [bbox, (text, confidence)]
        texts, scores, polys = [], [], []
        for item in ocr_res:
            if not item or len(item) < 2:
                continue
            box, rec = item[0], item[1]
            if not rec or len(rec) < 2:
                continue
            texts.append(rec[0])
            scores.append(rec[1])
            polys.append(box)

    for i, text in enumerate(texts):
        conf = float(scores[i]) if i < len(scores) else 0.0
        box = polys[i] if i < len(polys) else None
        # 车牌过滤：按中国车牌形态匹配（汉字 + 字母数字）。
        # 注意 OCR 常把车牌里的数字 1 认成字母 I（如 苏ABDC11 → 苏ABDCII），
        # 所以不能强制要求"必须含数字"，否则会误杀合法车牌。
        clean = re.sub(r'[^A-Z0-9\u4e00-\u9fa5]', '', str(text).upper())
        if PLATE_PATTERN.match(clean):
            plates.append({
                'plate_number': clean,
                'confidence': round(conf, 4),
                'bbox': [[int(p[0]), int(p[1])] for p in box] if box is not None else []
            })
    return plates

def mock_result(detect_type):
    """模型不可用时返回模拟数据，便于联调"""
    if detect_type == 'flame':
        return {
            'objects': [{'label': 'flame', 'confidence': 0.92, 'bbox': [100, 100, 200, 200]}],
            'plates': [],
            'mock': True
        }
    elif detect_type == 'person':
        return {
            'objects': [
                {'label': 'resident', 'confidence': 0.88, 'bbox': [50, 50, 150, 300]},
                {'label': 'outsider', 'confidence': 0.76, 'bbox': [200, 50, 300, 300]}
            ],
            'plates': [],
            'mock': True
        }
    elif detect_type == 'plate':
        return {
            'objects': [],
            'plates': [{'plate_number': 'A12345', 'confidence': 0.95, 'bbox': [[10,10],[100,10],[100,40],[10,40]]}],
            'mock': True
        }
    return {'objects': [], 'plates': [], 'mock': True}

# ========== HTTP 接口 ==========

@app.errorhandler(Exception)
def handle_exception(e):
    print(f"[AI] 未捕获异常: {type(e).__name__}: {e}", flush=True)
    traceback.print_exc()
    return jsonify({'error': f'{type(e).__name__}: {str(e)}'}), 500

@app.route('/recognize', methods=['POST'])
def recognize():
    data = request.get_json(force=True)
    image_path = data.get('image_path', '')
    detect_type = data.get('detect_type', '')
    source = data.get('source', 'local')  # board=T5AI-Board 板子, local=robot_client 本地摄像头

    if not image_path or not detect_type:
        return jsonify({'error': '缺少 image_path 或 detect_type'}), 400

    if detect_type not in ('flame', 'person', 'plate'):
        return jsonify({'error': 'detect_type 必须是 flame / person / plate'}), 400

    # 图片路径解析：后端存的是 /uploads/日期/文件名.jpg
    # 实际文件在 backend/uploads/日期/文件名.jpg
    # 支持多种输入形式
    candidates = []
    # 1. 绝对路径直接用
    if os.path.isabs(image_path):
        candidates.append(image_path)
    # 2. 去掉开头的 /uploads/ 拼到后端上传目录
    if image_path.startswith('/uploads/'):
        rel = image_path[len('/uploads/'):]
        candidates.append(os.path.join(UPLOAD_BASE, rel))
        # 后端从项目根目录启动时，上传目录在项目根/uploads
        candidates.append(os.path.join(BASE_DIR, '..', 'uploads', rel))
    # 3. 兼容旧 Node 后端目录
    candidates.append(os.path.join(BASE_DIR, '..', 'server', 'uploads', os.path.basename(image_path)))
    # 4. 只用文件名在后端上传目录下查找
    candidates.append(os.path.join(UPLOAD_BASE, os.path.basename(image_path)))

    img = None
    actual_path = None
    for p in candidates:
        norm = os.path.normpath(p)
        if os.path.exists(norm):
            img = cv2.imread(norm)
            if img is not None:
                actual_path = norm
                break

    if img is None:
        return jsonify({'error': f'图片不存在: {image_path}', 'tried': candidates}), 404

    result = {'objects': [], 'plates': [], 'detect_type': detect_type, 'mock': False}

    try:
        if detect_type == 'flame':
            result['objects'] = detect_multi(img)
        elif detect_type == 'person':
            result['objects'] = detect_person(img)
        elif detect_type == 'plate':
            result['plates'] = detect_plate(img)
    except Exception as e:
        print(f"[AI] 识别异常: {e}", flush=True)
        traceback.print_exc()
        return jsonify({'error': f'识别失败: {str(e)}'}), 500

    # mock 模式兜底
    if mock_mode and not result['objects'] and not result['plates']:
        result = mock_result(detect_type)

    # 保存标注后的图片
    fname = f"{detect_type}_{datetime.now():%Y%m%d_%H%M%S}.jpg"
    # source=board → T5AI-Board 板子的图存 outputs/；source=local → robot_client 本地存 outputs_local/
    save_dir = OUTPUT_DIR if source == 'board' else OUTPUT_LOCAL_DIR
    annotated_path = os.path.join(save_dir, fname)
    try:
        cv2.imwrite(annotated_path, img)
        result['annotated_path'] = os.path.relpath(annotated_path, BASE_DIR)
    except Exception as e:
        print(f"[AI] 保存标注图失败: {e}", flush=True)

    result['timestamp'] = int(time.time() * 1000)
    result['source'] = source
    return jsonify(result)

@app.route('/health', methods=['GET'])
def health():
    return jsonify({
        'status': 'ok',
        'yolo_loaded': yolo_model is not None,
        'duo_loaded': duo_model is not None,
        'ocr_loaded': ocr_engine is not None,
        'mock_mode': mock_mode,
        'model_dir': MODEL_DIR
    })

if __name__ == '__main__':
    print("=" * 60)
    print("AI 识别服务启动中...")
    print(f"  模型目录: {MODEL_DIR}")
    print(f"  输出目录: {OUTPUT_DIR}")
    print("=" * 60)

    # 预加载模型（首次调用时也会加载，这里提前加载加速首请求）
    load_yolo()
    load_duo()
    load_ocr()

    if mock_mode:
        print("\n[AI] ⚠️ 当前为 MOCK 模式（部分模型不可用）")
        print("[AI]    返回模拟识别结果，可用于前后端联调")
        print(f"[AI]    要启用真实识别，请将模型放到 {MODEL_DIR}/ 并安装依赖")
    else:
        print("\n[AI] ✅ 所有模型加载成功，真实识别模式")

    print("\n[AI] 服务监听 http://localhost:5000")
    app.run(host='0.0.0.0', port=5000, debug=False)
