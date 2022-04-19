package aiconnector.collide;

import aiconnector.setting.AIConstants;
import aiconnector.utils.tuple.Tuple;

import java.util.Objects;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;

public class Utils {
    /**
     *	功能: 判断两条直线重合.<p>
     *	判断条件：满足L1的直线方程：<p>
     *	且 p3 != p4 && a1*x3 + b1 * y3 + c1 == 0 && a1*x4 + b1 * y4 + c1 == 0
     */
    public static boolean line_conincdence(Vec4d l1, Vec4d l2) {
        long x1 = l1.at(0), y1 = l1.at(1), x2 = l1.at(2), y2 = l1.at(3);
        long a1 = -(y2 - y1), b1 = x2 - x1, c1 = (y2 - y1) * x1 - (x2 - x1) *y1;
        long x3 = l2.at(0), y3 = l2.at(1), x4 = l2.at(2), y4 = l2.at(3);
        return (x3 != x4 || y3 != y4) &&
                a1 * x3 + b1 * y3 + c1 == 0 && a1 * x4 + b1 * y4 + c1 == 0;
    }
    /**
     * 功能：通过距离检查是否存在冲突.<p>
     *  条件：1、点到线的距离
     */
    public static boolean check_conincdence_by_distance(Vec4d l1, Vec4d l2) {
        double distance = point_line_distance(l1, l2);

        if (distance < AIConstants.POINT_SPACE) {
            /******************************************************************************************************************
             在距离小于的某个值的线里面，有些是一些不冲突的线延伸导致的冲突，这些需要排除。如：1、同一个区域的反向线；2、不同区域的延长线（包括正向线、反向线）。
             这里面我们首先排除反向线.以下根据向量算法方向的理论判定。
             *******************************************************************************************************************/
            Tuple<Double,Double> p1 = vector_direction(l1);
            Tuple<Double,Double> p2 =vector_direction(l2);
            return Objects.equals(p1.a, p2.a) && Objects.equals(p1.b, p2.b);
        }

        return false;
    }
    /**
     * 功能：计算点到直线的距离<p>
     * l1: 直线<p>
     * l2：随便取第一个或第二个点即可
     */
    public static double point_line_distance(Vec4d l1, Vec4d l2) {
        long x1 = l1.at(0), y1 = l1.at(1), x2 = l1.at(2), y2 = l1.at(3);
        long a1 = -(y2 - y1), b1 = x2 - x1, c1 = (y2 - y1) * x1 - (x2 - x1) *y1;
        long x3 = l2.at(0), y3 = l2.at(1), x4 = l2.at(2), y4 = l2.at(3);

        return abs(a1*x3 + b1 * y3 + c1) / sqrt(a1*a1 + b1 * b1);
    }
    /**
     * 功能：计算两个点的向量方向(以单位向量标识)
     */
    public static Tuple<Double, Double> vector_direction(Vec4d l1) {
        long x1 = l1.at(0), y1 = l1.at(1), x2 = l1.at(2), y2 = l1.at(3);
        // 向量 = 向量2-向量1
        long Vx = y2 - y1, Vy = x2 - x1;
        double length = sqrt(Vx*Vx + Vy * Vy);
        var u0x = (1/ length)*Vx;
        var u0y = (1 / length)*Vy;
        return Tuple.of( u0x,u0y );
    }
};
