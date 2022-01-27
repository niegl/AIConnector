package aiconnector.connector;

import aiconnector.manager.AIManager;
import aiconnector.utils.tuple.Triple;
import aiconnector.utils.tuple.Tuple;
import lombok.Getter;
import lombok.Setter;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import static aiconnector.connector.AIDirection.*;
import static java.lang.Math.*;
import static java.util.logging.Level.INFO;


public class AIConnector {
    /** 使用java自带的log工具 */
    private static final Logger logger = Logger.getLogger(AIConnector.class.getName());
    static {
        logger.setLevel(INFO);
    }
    /**
     * 连线上位置点集合
     */
    @Getter
    private final CopyOnWriteArrayList<Point> _route = new CopyOnWriteArrayList<>();

    @Getter
    private int _connector_id = 0;
    /**
     * 唯一代表这条连线的id
     */
    private static final AtomicInteger  _connector_atomic = new AtomicInteger(0);

    /**
     * 源、目标图元
     */
    @Getter
    @Setter
    private AIRectangle _srcRect;
    @Getter
    @Setter
    private AIRectangle _dstRect;

    private final ReadWriteLock _readWriteLock = new ReentrantReadWriteLock();
    /**
     * 初始目标点
     */
    @Getter
    private Point endPoint;

    public AIConnector(AIRectangle _srcRect, AIRectangle _dstRect) {
        _connector_id = _connector_atomic.incrementAndGet();
        this._srcRect = _srcRect;
        this._dstRect = _dstRect;
    }

    /**
     * 主入口，搜索路径
     * @param lpSrcRect 源图元
     * @param lpDstRect 目标图元
     * @return 路径点集合
     */
    List<Point> search_route(Rectangle lpSrcRect, Rectangle lpDstRect) {
        synchronized (this) {
            set_srcRect(_srcRect);
            set_dstRect(_dstRect);
        }

        Objects.requireNonNull(_srcRect);
        Objects.requireNonNull(_dstRect);
        logger.info("------------------------------------>>>[搜索开始]<<<-------------------------------------");
        AIDirection direction = init_direction(lpSrcRect, lpDstRect);
        if (direction.equals(UNKOWN)) {
            return get_route();
        }
        Triple<AIDirection, Point, Point> guid = init_point(direction, _srcRect, _dstRect);

        _route.add(guid.b);

        processPoints(guid.b, direction,new Tuple<>(null,null),new Tuple<>(null,null));

        logger.info("------------------------------------>>>[搜索结束]<<<-------------------------------------");
        return get_route();
    }

    private void processPoints(Point startPoint, AIDirection direction,
                               Tuple<Rectangle, TrapData> parallelBarrierTuple,
                               Tuple<Rectangle, TrapData> prevParallelBarrierTuple) {
        // 迷路了就回家吧
        if (_route.size() > 13)
        {
            logger.info("process error：route size exceed limit: 13" );
            return;
        }

        if (direction == UP)
            processUp(startPoint, parallelBarrierTuple, prevParallelBarrierTuple);
        else if (direction == DOWN)
            processDown(startPoint, parallelBarrierTuple, prevParallelBarrierTuple);
        else if (direction == LEFT)
            processLeft(startPoint, parallelBarrierTuple, prevParallelBarrierTuple);
        else if (direction == RIGHT)
            processRight(startPoint, parallelBarrierTuple, prevParallelBarrierTuple);
        else if (direction == LEFT_UP)
        {
        }
        else if (direction == LEFT_DOWN)
        {
        }
        else if (direction == RIGHT_UP)
        {
        }
        else if (direction == RIGHT_DOWN)
        {
        }

    }

    private void processUp(Point startPoint,
                           Tuple<Rectangle, TrapData> parallelBarrierTuple,
                           Tuple<Rectangle, TrapData> prevParallelBarrierTuple) {
        Point newStartPoint = (Point) startPoint.clone();
    }
    private void processDown(Point startPoint,
                             Tuple<Rectangle, TrapData> parallelBarrierTuple,
                             Tuple<Rectangle, TrapData> prevParallelBarrierTuple) {
        Point newStartPoint = (Point) startPoint.clone();
    }
    private void processLeft(Point startPoint,
                             Tuple<Rectangle, TrapData> parallelBarrierTuple,
                             Tuple<Rectangle, TrapData> prevParallelBarrierTuple) {
        Point newStartPoint = (Point) startPoint.clone();
    }
    private void processRight(Point startPoint,
                              Tuple<Rectangle, TrapData> parallelBarrierTuple,
                              Tuple<Rectangle, TrapData> prevParallelBarrierTuple) {
        Point newStartPoint = (Point) startPoint.clone();
    }

    interface TrapProcessCallback {
        long trap_process(Point spPoint, AIDirection direction,
                          Rectangle spBarrier_inflate, Rectangle spFront, TrapData trap_data, TrapData inverse_trap_data);
    }

    /**
     * 用来计算陷阱的边缘。
     */
    class TrapCallback implements TrapProcessCallback {

        @Override
        public long trap_process(Point spPoint, AIDirection direction,
                                 Rectangle spBarrier_inflate, Rectangle spFront, TrapData trap_data, TrapData inverse_trap_data) {
            // 可以将本地参数带进来:std::shared_ptr<AIVector> vector, std::shared_ptr<POINT> spPoint
            // 修正 四边大小
            int left = spBarrier_inflate.x;
            int top = spBarrier_inflate.y;
            int right = spBarrier_inflate.x + spBarrier_inflate.width;
            int bottom = spBarrier_inflate.y + spBarrier_inflate.height;

            Rectangle cloneDst = null;
            try {
                _readWriteLock.readLock().lock();
                cloneDst = (Rectangle) _dstRect.clone();
            } finally {
                _readWriteLock.readLock().unlock();
            }

            final int spFrontBottom = spFront.y + spFront.height;
            final int spFrontRight = spFront.x + spFront.width;
            switch (direction) {

                case LEFT -> {
                    /** 解决情况5的异常。考虑目标图元的左面无效部分图元，使宽度计算更精准*/
                    if (spFront.x + spFront.width > cloneDst.x)
                    {
                        top = min(spFront.y, spBarrier_inflate.y);
                        bottom = max(spFrontBottom, bottom);
                    }

                    if (spFrontBottom < spPoint.y)		// 上重叠
                    {
                        trap_data.transformByMax(spFrontRight, Long.MIN_VALUE);
                        // 条件保证增长是连续性的，不是跳跃的
                        if(spFrontRight >= inverse_trap_data._UpOrLeft) inverse_trap_data.transformByMin(spFront.x, Long.MAX_VALUE);
                    }
                    else if (spFront.y > spPoint.y)	// 下重叠
                    {
                        trap_data.transformByMax(Long.MIN_VALUE, spFrontRight);
                        if (spFrontRight >= inverse_trap_data._DownOrRight) inverse_trap_data.transformByMin(Long.MAX_VALUE, spFront.x);
                    }
                    else if ( spFront.y < spPoint.y && spFrontBottom > spPoint.y)	//	考虑在穿过的情况：只反向需要
                    {
                        if (spFrontRight >= inverse_trap_data._UpOrLeft) inverse_trap_data.transformByMin(spFront.x, Long.MAX_VALUE);
                        if (spFrontRight >= inverse_trap_data._DownOrRight) inverse_trap_data.transformByMin(Long.MAX_VALUE, spFront.x);
                    }
                }
                case RIGHT -> {
                    /** 解决情况5的异常。考虑目标图元的右面无效部分图元，使宽度计算更精准*/
                    if (spFront.x < cloneDst.x+cloneDst.width)
                    {
                        top = min(spFront.y, spBarrier_inflate.y);
                        bottom = max(spFrontBottom, bottom);
                    }

                    if (spFrontBottom < spPoint.y)		// 上重叠
                    {
                        trap_data.transformByMin(spFront.x, Long.MAX_VALUE);
                        if (spFront.x <= inverse_trap_data._UpOrLeft) inverse_trap_data.transformByMax(spFrontRight, Long.MIN_VALUE);
                    }
                    else if (spFront.y > spPoint.y)	// 下重叠
                    {
                        trap_data.transformByMin(Long.MAX_VALUE, spFront.x);
                        if (spFront.x <= inverse_trap_data._DownOrRight) inverse_trap_data.transformByMax(Long.MIN_VALUE, spFrontRight);
                    }
                    else if (spFront.y < spPoint.y &&  spPoint.y < spFrontBottom)
                    {
                        if (spFront.x <= inverse_trap_data._UpOrLeft) inverse_trap_data.transformByMax(spFrontRight, Long.MIN_VALUE);
                        if (spFront.x <= inverse_trap_data._DownOrRight) inverse_trap_data.transformByMax(Long.MIN_VALUE, spFrontRight);
                    }
                }
                case UP -> {
                    /** 解决情况5的异常。考虑目标图元的上面无效部分图元，使宽度计算更精准*/
                    if (spFrontBottom > cloneDst.y)
                    {
                        left = min(spFront.x, left);
                        right = max(spFrontRight, right);
                    }

                    if (spFrontRight < spPoint.x)		// 左重叠
                    {
                        trap_data.transformByMax(spFrontBottom, Long.MIN_VALUE);
                        if (spFrontBottom >= inverse_trap_data._UpOrLeft) inverse_trap_data.transformByMin(spFront.y, Long.MAX_VALUE);
                    }
                    else if (spFront.x > spPoint.x)	// 右重叠
                    {
                        trap_data.transformByMax(Long.MIN_VALUE, spFrontBottom);
                        if (spFrontBottom >= inverse_trap_data._DownOrRight) inverse_trap_data.transformByMin(Long.MAX_VALUE, spFront.y);
                    }
                    else if (spFront.x < spPoint.x && spPoint.x < spFrontRight)
                    {
                        if (spFrontBottom >= inverse_trap_data._UpOrLeft) inverse_trap_data.transformByMin(spFront.y, Long.MAX_VALUE);
                        if (spFrontBottom >= inverse_trap_data._DownOrRight) inverse_trap_data.transformByMin(Long.MAX_VALUE, spFront.y);
                    }
                }
                case DOWN -> {
                    /** 解决情况5的异常。考虑目标图元的下面无效部分图元，使宽度计算更精准*/
                    if (spFront.y < cloneDst.y+cloneDst.height)
                    {
                        left = min(spFront.x, left);
                        right = max(spFrontRight, right);
                    }

                    if (spFrontRight < spPoint.x)		// 左重叠
                    {
                        trap_data.transformByMin(spFront.y, Long.MAX_VALUE);
                        if (spFront.y <= inverse_trap_data._UpOrLeft) inverse_trap_data.transformByMax(spFrontBottom, Long.MIN_VALUE);
                    }
                    else if (spFront.x > spPoint.x)	// 右重叠
                    {
                        trap_data.transformByMin(Long.MAX_VALUE, spFront.y);
                        if (spFront.y <= inverse_trap_data._DownOrRight) inverse_trap_data.transformByMax(Long.MIN_VALUE, spFrontBottom);
                    }
                    else if (spFront.x < spPoint.x && spPoint.x < spFrontRight)
                    {
                        if (spFront.y <= inverse_trap_data._UpOrLeft) inverse_trap_data.transformByMax(spFrontBottom, Long.MIN_VALUE);
                        if (spFront.y <= inverse_trap_data._DownOrRight) inverse_trap_data.transformByMax(Long.MIN_VALUE, spFrontBottom);
                    }
                }
                case UNKOWN -> {

                }
            }

            spBarrier_inflate.setBounds(left, top, right-left, bottom-top);

            return 0;
        }
    }

    Rectangle trap_find( AIDirection direction, Point spPoint, AIRectangle spBarrier, TrapData trap_data
            , TrapData inverse_trap_data)
    {
        TrapData trapClone = null;
        TrapData inverseTrapClone = null;

        if (spBarrier != null) {
            if (direction == LEFT) {
                trapClone = trap_data.clone(spBarrier.x + spBarrier.width);
                inverseTrapClone = inverse_trap_data.clone(spBarrier.x);
            }
            else if (direction == RIGHT) {
                trapClone = trap_data.clone(spBarrier.x);
                inverseTrapClone = inverse_trap_data.clone(spBarrier.x + spBarrier.width);
            }
            else if (direction == UP) {
                trapClone = trap_data.clone(spBarrier.y+spBarrier.height);
                inverseTrapClone = inverse_trap_data.clone(spBarrier.y);
            }
            else if (direction == DOWN) {
                trapClone = trap_data.clone(spBarrier.y);
                inverseTrapClone = inverse_trap_data.clone(spBarrier.y+spBarrier.height);
            }
        }

        AIRectangle spBarrier_inflate = null;
        LinkedHashSet<AIRectangle> spQueue = new LinkedHashSet<>();


        //region 如果存在障碍,对障碍进行膨胀处理
        if (spBarrier != null)
        {
            spBarrier_inflate = (AIRectangle) spBarrier.clone();
            spQueue.add(spBarrier_inflate);

            CopyOnWriteArraySet<AIRectangle> overlap = AIManager.getInstance().getOverlap(spBarrier_inflate.get_table_id());
            if (overlap != null) {
                spQueue.addAll(overlap);
            }
        }
        //endregion

        BiFunction<Rectangle, Rectangle, Long> functionCallback = (Rectangle spBarrierInflate, Rectangle spFront) -> {
            TrapCallback callback1 = new TrapCallback();
            return callback1.trap_process(spPoint, direction, spBarrierInflate, spFront, trap_data, inverse_trap_data);
        };
        int position = 1;	//	处理到第几个位置
        tranverse_overlap(spQueue, position, spBarrier_inflate,functionCallback);

        return spBarrier_inflate;
    }

    void tranverse_overlap(LinkedHashSet<AIRectangle> spQueue, int position, Rectangle spBarrier_inflate,
                           BiFunction<Rectangle, Rectangle, Long> fnTrap)
    {
        // 如果队列为空，则结束返回
        if (spQueue.size() == position)
        {
            return;
        }

        var front = spQueue.stream().skip(position++).findFirst();
        if (front.isEmpty()) {
            return;
        }

        CopyOnWriteArraySet<AIRectangle> sub_overlap = AIManager.getInstance().getOverlap(front.get().get_table_id());
        spQueue.addAll(sub_overlap);

        // 还需要根据点和方向来计算出陷阱的两个边沿值。
        fnTrap.apply(spBarrier_inflate, front.get());
        //	spCurrent->inflate(left, top, right, bottom);

        // 调用队列
        tranverse_overlap(spQueue, position, spBarrier_inflate, fnTrap);
    }

    /**
     * 方向初始化
     * @param lpSrcRect
     * @param lpDstRect
     * @return
     */
    private static AIDirection init_direction(Rectangle lpSrcRect, Rectangle lpDstRect) {
        var xSrc = lpSrcRect.x;
        var ySrc = lpSrcRect.y;
        var widthSrc = lpSrcRect.width;
        var heightSrc = lpSrcRect.height;

        var xDst = lpDstRect.x;
        var yDst = lpDstRect.y;
        var widthDst = lpDstRect.width;
        var heightDst = lpDstRect.height;

        var centerSrc = new Point((int) lpSrcRect.getCenterX(), (int) lpSrcRect.getCenterY());
        var centerDst = new Point((int) lpDstRect.getCenterX(), (int) lpDstRect.getCenterY());

        // 计算宽度差、高度差
        var x_diff_center = abs(centerSrc.x - centerDst.x);
        var y_diff_center = abs(centerSrc.y - centerDst.y);

        // 垂直方向计算
        if (abs(centerSrc.x - centerDst.x) <= (widthSrc/2 + widthDst/2)) {
            if ((ySrc + heightSrc) < yDst) {
                return DOWN;
            }
            else if ((yDst + heightDst) < ySrc) {
                return UP;
            }
        }
        // 水平方向计算
        else if (abs(centerSrc.y - centerDst.y) <= (heightSrc/2 + heightDst/2)) {
            if ((xSrc + widthSrc) < xDst) {
                return RIGHT;
            }
            else if ((xDst + widthDst) < xSrc) {
                return LEFT;
            }
        }
        else if ((xDst + widthDst) < xSrc) {
            if ((yDst + heightDst) < ySrc) {
                return LEFT_UP;
            }
            else if (yDst > (ySrc + heightSrc)) {
                return LEFT_DOWN;
            }
        }
        else if ((xSrc+widthSrc)<xDst) {
            if ((ySrc + heightSrc) < yDst) {
                return RIGHT_DOWN;
            }
            else if (ySrc > (yDst + heightDst)) {
                return RIGHT_UP;
            }
        }
        else {
            return OVERLAP;
        }

        return UNKOWN;
    }

    /**
     * 初始化开始、终止点坐标.
     * @param lpSrcRect
     * @param lpDstRect
     * @return
     */
    private Triple<AIDirection,Point,Point> init_point(AIDirection direction, AIRectangle lpSrcRect, AIRectangle lpDstRect) {

        // 终点可以简单初始化为中心点（最后一步会进行修正）
        Point endPoint = new Point((int) lpDstRect.getCenterX(), (int) lpDstRect.getCenterY());
        Point startPoint = new Point((int) lpSrcRect.getCenterX(), (int) lpSrcRect.getCenterY());

        int up = (int) max(lpSrcRect.getY(), lpDstRect.getY());
        int down = (int) min(lpSrcRect.getY()+lpSrcRect.getHeight(), lpDstRect.getY()+lpDstRect.getHeight());
        int left = (int) max(lpSrcRect.getX(), lpDstRect.getX());
        int right = (int) min(lpSrcRect.getX()+lpSrcRect.getWidth(), lpDstRect.getX()+lpDstRect.getWidth());

        boolean horizontal = down >= up;	// 水平方向是否相交
        boolean vertical = right >= left;	// 垂直方向是否相交

        int connectorId = get_connector_id();
//        AnchorManager instance = AnchorManager.getInstance();
        Tuple<Point, AIDirection> guide = null;

        if (horizontal)
        {
            if (LEFT == direction)
            {
                guide = lpSrcRect.get_free_anchor(connectorId, new Point(lpSrcRect.x, up), LEFT, true);
            }
            else if (RIGHT == direction)
            {
                guide = lpSrcRect.get_free_anchor(connectorId, new Point(lpSrcRect.x+lpSrcRect.width, up), RIGHT,false);
            }
        }
        else if (vertical)
        {
            if (UP == direction)
            {
                guide = lpSrcRect.get_free_anchor( connectorId, new Point(left, lpSrcRect.y), UP, false);
            }
            else if (DOWN == direction)
            {
                guide = lpSrcRect.get_free_anchor( connectorId, new Point(left, lpSrcRect.y+lpSrcRect.height), DOWN, true);
            }
        }
        else
        {
            if (LEFT_UP == direction)
            {
                guide = lpSrcRect.get_free_anchor( connectorId, new Point(lpSrcRect.x, lpSrcRect.y+lpSrcRect.height/2), LEFT, false);
            }
            else if(LEFT_DOWN == direction)
            {
                guide = lpSrcRect.get_free_anchor( connectorId, new Point(lpSrcRect.x, lpSrcRect.y+lpSrcRect.height/2), LEFT, true);
            }
            else if (RIGHT_UP == direction)
            {
                guide = lpSrcRect.get_free_anchor( connectorId, new Point(lpSrcRect.x+lpSrcRect.width, lpSrcRect.y+lpSrcRect.height/2), RIGHT, true);
            }
            else if (RIGHT_DOWN == direction)
            {
                guide = lpSrcRect.get_free_anchor( connectorId, new Point(lpSrcRect.x+lpSrcRect.width, lpSrcRect.y+lpSrcRect.height/2), RIGHT, false);
            }
        }

        // solve the overlap problem
        if (direction == OVERLAP)
        {
            guide = lpSrcRect.get_free_anchor( connectorId, new Point(lpSrcRect.x+lpSrcRect.width, lpSrcRect.y+lpSrcRect.height/2), RIGHT, false);
        }

        assert guide != null;
        return new Triple<>(guide.b,guide.a,endPoint);
    }
}
