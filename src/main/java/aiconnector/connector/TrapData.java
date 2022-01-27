package aiconnector.connector;

import static aiconnector.connector.TRAP_TYPE.*;

/**
 * 利用状态机原理，判定陷阱类型
 */
public class TrapData {
    private TRAP_TYPE _trap_type;
    /**
     * left:上/下的左边；up:左/右的上边
     */
    long _UpOrLeft;
    /**
     * right:上/下的右边；down:左/右下边
     */
    long _DownOrRight;
    /**
     * 障碍的最近边，也就是前进距离
     */
    private long _forward;

    public TrapData(long _forward) {
        this._trap_type = TRAP_NONE;
        this._UpOrLeft = this._DownOrRight = this._forward = _forward;
    }

    public TrapData clone(long forward) { return new TrapData(forward); }

    /**
     * 为了产生一个障碍检测步骤缩小走形最大距离
     * @param num
     */
    public void decrease(int num) {
        _forward -= num; _UpOrLeft -= num; _DownOrRight -= num;
    }

    /**
     * 开口向上或向下时的判定：减小坐标值
     * @param UpOrLeft
     * @param DownOrRight
     */
    public void transformByMin( long UpOrLeft,  long DownOrRight) {
        transform(UpOrLeft < _UpOrLeft, DownOrRight < _DownOrRight, UpOrLeft, DownOrRight);
    }

    /**
     * 开口向下或向右时的判定：增加坐标值
     * @param UpOrLeft
     * @param DownOrRight
     */
    public void transformByMax( long UpOrLeft,  long DownOrRight) {
        transform(UpOrLeft > _UpOrLeft, DownOrRight > _DownOrRight, UpOrLeft, DownOrRight);
    }

    private void transform(boolean UpOrLeftComapre, boolean DownOrRightComapre, long UpOrLeft, long DownOrRight) {
        switch (_trap_type)
        {
            case TRAP_NONE:
                if (UpOrLeftComapre)
                {
                    _trap_type = TRAP_UPORLEFT_EDGE; _UpOrLeft = UpOrLeft;
                }
                else if (DownOrRightComapre)
                {
                    _trap_type = TRAP_DOWNORRIGHT_EDGE; _DownOrRight = DownOrRight;
                }
                break;
            case TRAP_UPORLEFT_EDGE:
                if (DownOrRightComapre)
                {
                    _trap_type = TRAP_TRAP; _DownOrRight = DownOrRight;
                }
                else if (UpOrLeftComapre)
                {
                    _trap_type = TRAP_UPORLEFT_EDGE; _UpOrLeft = UpOrLeft;
                }
                break;
            case TRAP_DOWNORRIGHT_EDGE:
                if (UpOrLeftComapre)
                {
                    _trap_type = TRAP_TRAP; _UpOrLeft = UpOrLeft;
                }
                else if (DownOrRightComapre)
                {
                    _trap_type = TRAP_DOWNORRIGHT_EDGE; _DownOrRight = DownOrRight;
                }
                break;
            case TRAP_TRAP:
                if (UpOrLeftComapre)
                {
                    _trap_type = TRAP_TRAP; _UpOrLeft = UpOrLeft;
                }
                else if (DownOrRightComapre)
                {
                    _trap_type = TRAP_TRAP; _DownOrRight = DownOrRight;
                }
                break;
            default:
                break;
        }
    }
}
