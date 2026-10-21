package cn.sju.backend.controller;

import cn.sju.backend.common.Result;
import cn.sju.backend.common.ResultCode;
import cn.sju.backend.dto.LoginDTO;
import cn.sju.backend.dto.RegisterDTO;
import cn.sju.backend.entity.SysUser;
import cn.sju.backend.mapper.SysUserMapper;
import cn.sju.backend.util.JwtUtil;
import cn.sju.backend.vo.TokenVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${jwt.expiration:86400000}")
    private Long expiration;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result<TokenVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        SysUser user = sysUserMapper.selectOne(
                new QueryWrapper<SysUser>().eq("user_name", loginDTO.getUserName())
        );
        if (user == null || !passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        if (!"正常".equals(user.getAccountStatus())) {
            return Result.fail(ResultCode.FORBIDDEN);
        }
        String token = jwtUtil.generateToken(
                user.getUserId(),
                user.getUserName(),
                user.getRole(),
                user.getCommunityId()
        );
        return Result.success(new TokenVO(token, expiration));
    }

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterDTO registerDTO) {
        Long count = sysUserMapper.selectCount(
                new QueryWrapper<SysUser>().eq("user_name", registerDTO.getUserName())
        );
        if (count != null && count > 0) {
            return Result.fail(ResultCode.BAD_REQUEST);
        }
        SysUser user = new SysUser();
        user.setUserName(registerDTO.getUserName());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setCommunityId(registerDTO.getCommunityId());
        user.setPhone(registerDTO.getPhone());
        user.setName(registerDTO.getName());
        user.setRole(registerDTO.getRole() == null ? "普通用户" : registerDTO.getRole());
        user.setAccountStatus("正常");
        sysUserMapper.insert(user);
        return Result.success(user.getUserId());
    }
}
