package tech.jxing.returnvision.common.exception;

/**
 * 【公共模块】运单号重复异常
 *
 * 错误码：2002
 * 触发场景：上传时 waybill_no 已存在 status=synced 的记录，阻断重复写飞书
 * 关联：docs/04 第 4.6 节 F11
 */
public class DuplicateWaybillError extends BizException {
    public DuplicateWaybillError(String msg) {
        super(2002, msg);
    }
}
