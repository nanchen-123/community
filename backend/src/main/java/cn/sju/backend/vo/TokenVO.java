package cn.sju.backend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;

    private String tokenType = "Bearer";

    private Long expiresIn;

    public TokenVO(String token, Long expiresIn) {
        this.token = token;
        this.expiresIn = expiresIn;
    }
}
