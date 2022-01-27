package aiconnector.connector;

/**
 * 陷阱类型的定义.<p>
 * 说明：1、对陷阱类型进行了合并：上和左，右和下。
 */
public enum TRAP_TYPE {
    TRAP_NONE,
    TRAP_UPORLEFT_EDGE,	// 由上或左引起的单边trap
    TRAP_DOWNORRIGHT_EDGE,	// 由下或右引起的单边trap
    TRAP_TRAP,	// trap
}
