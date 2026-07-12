package tech.jxing.returnvision.common;

import lombok.Data;

/**
 * 【公共模块】统一响应结果
 *
 * 职责：封装所有API接口的统一返回格式 {code, msg, data}
 */
@Data
public class ResponseResult<T> {
    private int code;
    private String msg;
    private T data;

    /**
     * 成功响应
     *
     * @param data 业务数据
     * @return ResponseResult 含成功状态码0和数据
     */
    public static <T> ResponseResult<T> success(T data) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(0);
        result.setMsg("success");
        result.setData(data);
        return result;
    }

    /**
     * 失败响应
     *
     * @param code 错误码（非0）
     * @param msg  错误描述
     * @return ResponseResult 含错误码和错误信息
     */
    public static <T> ResponseResult<T> error(int code, String msg) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(null);
        return result;
    }
}
