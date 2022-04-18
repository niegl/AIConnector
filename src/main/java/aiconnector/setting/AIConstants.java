package aiconnector.setting;

import aiconnector.connector.AIDirection;

import java.util.List;
import static aiconnector.connector.AIDirection.*;

public interface AIConstants {
    int MAX_STEP = 13;
    /**
     * 是否开启日志记录查找过程(debug使用)
     */
    boolean log = false;
    /**
     * 矩形图元上锚点间距
     */
    int POINT_SPACE = 10;
    /**
     * 线、图元或障碍之间的距离
     */
    int BARRIER_SPACE = 10;
    int POINT2EDGE_GAP = 10;
    /**
     * 如果存在线冲突，则尝试解决冲突的最大次数
      */
    int MAX_RETRY_TIMES = 6;
    /**
     * 设置是否在完成路径搜索后对路径进行优化(线路合并)
     */
    boolean optimize = false;
    /**
     * 是否路径居中(位于两个障碍物中间)
     */
    boolean middleRoute = false;
    /**
     * 直线方向数组
     */
    List<AIDirection> m_straightLineArr = List.of(DOWN, UP, LEFT, RIGHT);
    /**
     * 折线集合
     */
    List<AIDirection> m_brokenLineArr = List.of( LEFT_UP,RIGHT_UP,LEFT_DOWN,RIGHT_DOWN );
}
