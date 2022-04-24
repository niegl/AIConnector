package aiconnector.connector;

import static aiconnector.connector.TRAP_TYPE.*;

/**
 * 利用状态机原理，判定陷阱类型
 */
class TrapData {
    TRAP_TYPE _trap_type = TRAP_NONE;
    /**
     * left:上/下的左边；up:左/右的上边
     */
    int _UpOrLeft = 0;
    /**
     * right:上/下的右边；down:左/右下边
     */
    int _DownOrRight = 0;
    /**
     * 障碍的最近边，也就是前进距离
     */
    int _forward;

    public void init(int _forward) {
        this._UpOrLeft = this._DownOrRight = this._forward = _forward;
    }

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
    public void transformByMin( int UpOrLeft,  int DownOrRight) {
        transform(UpOrLeft < _UpOrLeft, DownOrRight < _DownOrRight, UpOrLeft, DownOrRight);
    }

    /**
     * 开口向下或向右时的判定：增加坐标值
     * @param UpOrLeft
     * @param DownOrRight
     */
    public void transformByMax( int UpOrLeft,  int DownOrRight) {
        transform(UpOrLeft > _UpOrLeft, DownOrRight > _DownOrRight, UpOrLeft, DownOrRight);
    }

    private void transform(boolean UpOrLeftComapre, boolean DownOrRightComapre, int UpOrLeft, int DownOrRight) {
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
