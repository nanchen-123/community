package cn.sju.backend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录响应：token + 基本用户信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;

    private String tokenType = "Bearer";

    /** 过期剩余毫秒数 */
    private Long expiresIn;

    /** 用户简要信息 */
    private UserInfo user;

    public TokenVO(String token, Long expiresIn, UserInfo user) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.user = user;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long userId;
        private String userName;
        private String name;
        private String role;
        private Long communityId;
    }
}
