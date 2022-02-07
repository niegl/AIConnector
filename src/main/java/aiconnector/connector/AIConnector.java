package aiconnector.connector;

import aiconnector.collide.Utils;
import aiconnector.collide.Vec4d;
import aiconnector.manager.AIManager;
import aiconnector.setting.AIConstants;
import aiconnector.utils.tuple.Triple;
import aiconnector.utils.tuple.Tuple;
import lombok.Getter;

import java.awt.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import static aiconnector.connector.AIDirection.*;
import static aiconnector.setting.AIConstants.BARRIER_SPACE;
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
    private AtomicReference<AIRectangle> _srcRect = new AtomicReference<AIRectangle>();
    private AtomicReference<AIRectangle> _dstRect = new AtomicReference<AIRectangle>();

    private final ReadWriteLock _readWriteLock = new ReentrantReadWriteLock();
    /**
     * 初始目标点
     */
    private AtomicReference<Point> _endPoint = new AtomicReference<Point>();

    public AIConnector(AIRectangle _srcRect, AIRectangle _dstRect) {
        _connector_id = _connector_atomic.incrementAndGet();
        this._srcRect.set(_srcRect);
        this._dstRect.set(_dstRect);
    }

    /**
     * 主入口，搜索路径
     * @param lpSrcRect 源图元
     * @param lpDstRect 目标图元
     * @return 路径点集合
     */
    List<Point> search_route(Rectangle lpSrcRect, Rectangle lpDstRect) {
        synchronized (this) {
            this._srcRect.set(new AIRectangle(lpSrcRect));
            this._dstRect.set(new AIRectangle(lpDstRect));
        }

        Objects.requireNonNull(_srcRect);
        Objects.requireNonNull(_dstRect);
        logger.info("------------------------------------>>>[搜索开始]<<<-------------------------------------");
        AIDirection direction = init_direction(lpSrcRect, lpDstRect);
        if (direction.equals(UNKOWN)) {
            return get_route();
        }
        Triple<AIDirection, Point, Point> guid = init_point(direction, _srcRect.get(), _dstRect.get());

        _route.add(guid.b);

        processPoints(guid.b, direction,new Tuple<>(null,null),new Tuple<>(null,null));

        logger.info("------------------------------------>>>[搜索结束]<<<-------------------------------------");
        return get_route();
    }

    private void processPoints(Point startPoint, AIDirection direction,
                               Tuple<AIRectangle, TrapData> parallelBarrierTuple,
                               Tuple<AIRectangle, TrapData> prevParallelBarrierTuple) {
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
                           Tuple<AIRectangle, TrapData> parallelBarrierTuple,
                           Tuple<AIRectangle, TrapData> prevParallelBarrierTuple) {
        Point newStartPoint = (Point) startPoint.clone();
        AIRectangle prevParallelBarrier = null, parallelBarrier = null, barrier = null;
        TrapData prevParallelInverse_trap, parallelInverse_trap, inverse_trap, trap;

        // 获取当前的障碍及正反向陷阱
        parallelBarrier = parallelBarrierTuple.a;
        parallelInverse_trap = parallelBarrierTuple.b;
        prevParallelBarrier = prevParallelBarrierTuple.a;
        prevParallelInverse_trap = prevParallelBarrierTuple.b;
        var barrierTriple = barrier_find(startPoint, UP, parallelBarrier);
        barrier = barrierTriple.a;
        trap = barrierTriple.b;
        inverse_trap = barrierTriple.c;

//region 只考虑障碍的情况下，计算最远距离
        // 没有障碍
        AIRectangle targetRect = _dstRect.get();
        if (null == barrier)
        {
            // 没有平行障碍
            if (null == parallelBarrier)
            {
                if (startPoint.x >= targetRect.x && startPoint.x <= targetRect.right)
                {
                    _endPoint.set(new Point(startPoint.x,targetRect.bottom));
                    _route.add(_endPoint.get());
                    return;
                }
            }
            // 没有障碍的情况下，能够向上走得最远距离（因为反向行走的情况，需要考虑平行障碍来纠正方向）
            newStartPoint.y = min(_endPoint.get().y, parallelBarrier != null ? parallelBarrier.y - BARRIER_SPACE : Integer.MAX_VALUE);
            if (prevParallelBarrier != null)	//	需要绕过反向障碍，无论是否存在陷阱(修复异常情况4)
            {
                if (newStartPoint.x < prevParallelBarrier.x)
                {
                    newStartPoint.y = min(newStartPoint.y, prevParallelInverse_trap._UpOrLeft + BARRIER_SPACE); // 产生障碍
                }
                else
                {
                    newStartPoint.y = min(newStartPoint.y, prevParallelInverse_trap._DownOrRight + BARRIER_SPACE);// 产生障碍
                }
            }
        }
        // 存在障碍
        else {
            // 不存在陷阱
            if (trap._trap_type == TRAP_TYPE.TRAP_NONE)
            {
                newStartPoint.y = barrier.bottom + BARRIER_SPACE;
            }
            else
            {
                newStartPoint.y = max(trap._DownOrRight, trap._UpOrLeft)+ BARRIER_SPACE;
            }
        }
//endregion

//region 计算下一步最优移动方向, 不考虑平行障碍
        AIDirection nextDirection = LEFT;// 初始化下一步方向
        // 没有障碍
        if (null == barrier)
        {
            if (newStartPoint.x < _endPoint.get().x) nextDirection = RIGHT;
        }
        else
        {
            if (_endPoint.get().x < barrier.x)
            {
                nextDirection = LEFT;
            }
            else
            {
                if (_endPoint.get().x > barrier.right)
                {
                    nextDirection = RIGHT;
                }
                else
                {
                    boolean best = abs(_endPoint.get().x - barrier.x) + abs(newStartPoint.x - barrier.x) + abs(inverse_trap._UpOrLeft - barrier.y) + abs(inverse_trap._UpOrLeft - _endPoint.get().y) >
                            abs(barrier.right - newStartPoint.x) + abs(barrier.right - _endPoint.get().x) + abs(inverse_trap._DownOrRight - barrier.y) + abs(inverse_trap._DownOrRight - _endPoint.get().y);
                    nextDirection = best ? RIGHT : LEFT;
                }
            }
        }
//endregion
//region 检查最佳方向是否走的桶（进一步逼近半开陷阱）
        if (LEFT == nextDirection)
        {
            if (trap._trap_type == TRAP_TYPE.TRAP_DOWNORRIGHT_EDGE)
            {
                newStartPoint.y = min(newStartPoint.y, trap._forward + BARRIER_SPACE);
            }
        }
        else
        {
            if (trap._trap_type == TRAP_TYPE.TRAP_UPORLEFT_EDGE)
            {
                newStartPoint.y = min(newStartPoint.y, trap._forward + BARRIER_SPACE);
            }
        }
//endregion

        newStartPoint = get_point_not_conincdence(UP, newStartPoint, barrier, nextDirection, parallelBarrier, parallelInverse_trap);

        _route.add(newStartPoint);
        processPoints(newStartPoint, nextDirection, Tuple.of(barrier, inverse_trap), parallelBarrierTuple);

    }

    private void processDown(Point startPoint,
                             Tuple<AIRectangle, TrapData> parallelBarrierTuple,
                             Tuple<AIRectangle, TrapData> prevParallelBarrierTuple) {
        Point newStartPoint = (Point) startPoint.clone();
        AIRectangle prevParallelBarrier = null, parallelBarrier = null, barrier = null;
        TrapData prevParallelInverse_trap, parallelInverse_trap, inverse_trap, trap;

        // 获取当前的障碍及正反向陷阱
        parallelBarrier = parallelBarrierTuple.a;
        parallelInverse_trap = parallelBarrierTuple.b;
        prevParallelBarrier = prevParallelBarrierTuple.a;
        prevParallelInverse_trap = prevParallelBarrierTuple.b;
        var barrierTriple = barrier_find(startPoint, DOWN, parallelBarrier);
        barrier = barrierTriple.a;
        trap = barrierTriple.b;
        inverse_trap = barrierTriple.c;

//region 只考虑水平障碍的情况下，计算最远距离
        // 没有障碍
        AIRectangle targetRect = _dstRect.get();
        if (null == barrier)
        {
            // 没有平行障碍
            if (null == parallelBarrier)
            {
                if (startPoint.x >= targetRect.x && startPoint.x <= targetRect.right)
                {
                    _endPoint.set(new Point(startPoint.x,targetRect.y));
                    _route.add(_endPoint.get());
                    return;
                }
            }
            // 没有障碍的情况下，能够向下走得最远距离（因为反向行走的情况，需要考虑平行障碍来纠正方向）
            newStartPoint.y = max(_endPoint.get().y, parallelBarrier != null ? parallelBarrier.bottom + BARRIER_SPACE : Integer.MIN_VALUE);
            if (prevParallelBarrier != null)	//	需要绕过反向障碍，无论是否存在陷阱(修复异常情况4)
            {
                if (newStartPoint.x < prevParallelBarrier.x)
                {
                    newStartPoint.y = max(newStartPoint.y, prevParallelInverse_trap._UpOrLeft - BARRIER_SPACE);	//	这里减去为了产生障碍
                }
                else
                {
                    newStartPoint.y = max(newStartPoint.y, prevParallelInverse_trap._DownOrRight - BARRIER_SPACE);//	这里减去为了产生障碍
                }
            }
        }
        // 存在障碍
        else {
            // 不存在陷阱
            if (trap._trap_type == TRAP_TYPE.TRAP_NONE)
            {
                newStartPoint.y = barrier.y- BARRIER_SPACE;
            }
            else
            {
                newStartPoint.y = min(trap._DownOrRight, trap._UpOrLeft)- BARRIER_SPACE;
            }
        }
//endregion 只考虑障碍的情况下，计算最远距离

//region 计算下一步最优移动方向, 不考虑平行障碍
        AIDirection nextDirection = LEFT;// 初始化下一步方向
        // 没有障碍
        if (null == barrier)
        {
            if (newStartPoint.x < _endPoint.get().x) nextDirection = RIGHT;
        }
        else
        {
            if (_endPoint.get().x < barrier.x)
            {
                nextDirection = LEFT;
            }
            else
            {
                if (_endPoint.get().x > barrier.right)
                {
                    nextDirection = RIGHT;
                }
                else
                {
                    boolean best = abs(_endPoint.get().x - barrier.x) + abs(newStartPoint.x - barrier.x) + abs(inverse_trap._UpOrLeft-barrier.y) + abs(inverse_trap._UpOrLeft - _endPoint.get().y) >
                            abs(barrier.right - newStartPoint.x) + abs(barrier.right - _endPoint.get().x) + abs(inverse_trap._DownOrRight - barrier.y) + abs(inverse_trap._DownOrRight - _endPoint.get().y);
                    nextDirection = best ? RIGHT : LEFT;
                }
            }
        }
//endregion
//region 检查最佳方向是否走的桶（进一步逼近半开陷阱）
        if (LEFT == nextDirection)
        {
            if (trap._trap_type == TRAP_TYPE.TRAP_DOWNORRIGHT_EDGE)
            {
                newStartPoint.y = max(newStartPoint.y, trap._forward - BARRIER_SPACE);
            }
        }
        else
        {
            if (trap._trap_type == TRAP_TYPE.TRAP_UPORLEFT_EDGE)
            {
                newStartPoint.y = max(newStartPoint.y, trap._forward - BARRIER_SPACE);
            }
        }
//endregion

        newStartPoint = get_point_not_conincdence(DOWN, newStartPoint, barrier, nextDirection, parallelBarrier, parallelInverse_trap);

        _route.add(newStartPoint);
        processPoints(newStartPoint, nextDirection, Tuple.of(barrier, inverse_trap), parallelBarrierTuple);

    }

    private void processLeft(Point startPoint,
                             Tuple<AIRectangle, TrapData> parallelBarrierTuple,
                             Tuple<AIRectangle, TrapData> prevParallelBarrierTuple) {
        Point newStartPoint = (Point) startPoint.clone();

        AIRectangle prevParallelBarrier = null, parallelBarrier = null, barrier = null;
        TrapData prevParallelInverse_trap, parallelInverse_trap, inverse_trap, trap;

        // 获取当前的障碍及正反向陷阱
        parallelBarrier = parallelBarrierTuple.a;
        parallelInverse_trap = parallelBarrierTuple.b;
        prevParallelBarrier = prevParallelBarrierTuple.a;
        prevParallelInverse_trap = prevParallelBarrierTuple.b;
        var barrierTriple = barrier_find(startPoint, LEFT, parallelBarrier);
        barrier = barrierTriple.a;
        trap = barrierTriple.b;
        inverse_trap = barrierTriple.c;

//region 只考虑水平障碍的情况下，计算最远距离
        // 没有障碍
        if ( null == barrier)
        {
            // 没有平行障碍
            if (null == parallelBarrier)
            {
                AIRectangle dst = _dstRect.get();
                if (startPoint.y >= dst.y && startPoint.y <= dst.bottom)
                {
                    _endPoint.set(new Point(dst.right,startPoint.y));
                    _route.add(_endPoint.get());
                    return;
                }
            }
            // 没有障碍的情况下，能够向左走得最远距离（因为反向行走的情况，需要考虑平行障碍来纠正方向）
            newStartPoint.x = min(_endPoint.get().x,
                    parallelBarrier!=null ? parallelBarrier.x - BARRIER_SPACE : Integer.MAX_VALUE);
            if (prevParallelBarrier != null)	//	需要绕过反向障碍，无论是否存在陷阱(修复异常情况4)
            {
                if (newStartPoint.y < prevParallelBarrier.y)
                {
                    newStartPoint.x = min(newStartPoint.x, prevParallelInverse_trap._UpOrLeft + BARRIER_SPACE);// 产生障碍
                }
                else
                {
                    newStartPoint.x = min(newStartPoint.x, prevParallelInverse_trap._DownOrRight + BARRIER_SPACE);// 产生障碍
                }
            }
        }
        // 存在障碍
        else {
            // 不存在陷阱
            if (trap._trap_type == TRAP_TYPE.TRAP_NONE)
            {
                newStartPoint.x = barrier.right + BARRIER_SPACE;
            }
            else
            {
                newStartPoint.x = max(trap._DownOrRight, trap._UpOrLeft) + BARRIER_SPACE;
            }
        }
//endregion 只考虑水平障碍的情况下，计算最远距离

//region 计算下一步最优移动方向, 不考虑平行障碍
        AIDirection nextDirection = UP;// 初始化下一步方向
        // 没有障碍
        if (null == barrier)
        {
            if (newStartPoint.y < _endPoint.get().y) nextDirection = DOWN;
        }
        else
        {
            if (_endPoint.get().y < barrier.y)
            {
                nextDirection = UP;
            }
            else
            {
                if (_endPoint.get().y > barrier.bottom)
                {
                    nextDirection = DOWN;
                }
                else
                {
                    var best = abs(_endPoint.get().y - barrier.y) + abs(newStartPoint.y - barrier.y) + abs(inverse_trap._UpOrLeft - barrier.x) + abs(inverse_trap._UpOrLeft - _endPoint.get().x) >
                            abs(barrier.bottom - newStartPoint.y) + abs(barrier.bottom - _endPoint.get().y)+ abs(inverse_trap._DownOrRight - barrier.x) + abs(inverse_trap._DownOrRight - _endPoint.get().x);
                    nextDirection = best ? DOWN : UP;
                }
            }
        }
//endregion
//region 检查最佳方向是否走的通（进一步逼近半开陷阱）
        if (UP == nextDirection)
        {
            if (TRAP_TYPE.TRAP_DOWNORRIGHT_EDGE == trap._trap_type)
            {
                newStartPoint.x = min(newStartPoint.x, trap._forward + BARRIER_SPACE);
            }
        }
        else
        {
            if (TRAP_TYPE.TRAP_UPORLEFT_EDGE == trap._trap_type)
            {
                newStartPoint.x = min(newStartPoint.x, trap._forward + BARRIER_SPACE);
            }
        }
//endregion 检查最佳方向是否走的桶（考虑平行障碍并进行坐标修正）

        newStartPoint = get_point_not_conincdence(LEFT, newStartPoint, barrier, nextDirection, parallelBarrier, parallelInverse_trap);

        _route.add(newStartPoint);
        processPoints(newStartPoint, nextDirection, Tuple.of(barrier, inverse_trap), parallelBarrierTuple);

    }

    private void processRight(Point startPoint,
                              Tuple<AIRectangle, TrapData> parallelBarrierTuple,
                              Tuple<AIRectangle, TrapData> prevParallelBarrierTuple) {
        Point newStartPoint = (Point) startPoint.clone();
        AIRectangle prevParallelBarrier = null, parallelBarrier = null, barrier = null;
        TrapData prevParallelInverse_trap, parallelInverse_trap, inverse_trap, trap;

        // 获取当前的障碍及正反向陷阱
        parallelBarrier = parallelBarrierTuple.a;
        parallelInverse_trap = parallelBarrierTuple.b;
        prevParallelBarrier = prevParallelBarrierTuple.a;
        prevParallelInverse_trap = prevParallelBarrierTuple.b;
        var barrierTriple = barrier_find(startPoint, RIGHT, parallelBarrier);
        barrier = barrierTriple.a;
        trap = barrierTriple.b;
        inverse_trap = barrierTriple.c;

//region 只考虑水平障碍的情况下，计算最远距离
        // 没有障碍
        AIRectangle targetRect = _dstRect.get();
        if (null == barrier)
        {
            // 没有平行障碍
            if (null == parallelBarrier)
            {
                if (startPoint.y >= targetRect.y && startPoint.y <= targetRect.bottom)
                {
                    _endPoint.set(new Point(targetRect.x,startPoint.y));
                    _route.add(_endPoint.get());
                    return;
                }
            }
            // 没有障碍的情况下，能够向右走得最远距离（因为反向行走的情况，需要考虑平行障碍来纠正方向）
            newStartPoint.x = max(_endPoint.get().x, parallelBarrier != null ? parallelBarrier.right + BARRIER_SPACE : Integer.MIN_VALUE);
            if (prevParallelBarrier != null)	//	需要绕过反向障碍，无论是否存在陷阱(修复异常情况4)
            {	// 在反向障碍的上面还是下面
                if (newStartPoint.y < prevParallelBarrier.y)
                {
                    newStartPoint.x = max(newStartPoint.x, prevParallelInverse_trap._UpOrLeft - BARRIER_SPACE);// 产生障碍
                }
                else
                {
                    newStartPoint.x = max(newStartPoint.x, prevParallelInverse_trap._DownOrRight - BARRIER_SPACE);// 产生障碍
                }
            }
        }
        // 存在障碍
        else {
            // 不存在陷阱
            if (trap._trap_type == TRAP_TYPE.TRAP_NONE)
            {
                newStartPoint.x = barrier.x - BARRIER_SPACE;
            }
            else
            {
                newStartPoint.x = min(trap._DownOrRight, trap._UpOrLeft ) - BARRIER_SPACE;
            }
        }
//endregion 只考虑水平障碍的情况下，计算最远距离

//region 计算下一步最优移动方向, 不考虑平行障碍
        AIDirection nextDirection = UP;// 初始化下一步方向
        // 没有障碍
        if (null == barrier)
        {
            if (newStartPoint.y < _endPoint.get().y) nextDirection = DOWN;
        }
        else
        {
            if (_endPoint.get().y < barrier.y)
            {
                nextDirection = UP;
            }
            else
            {
                if (_endPoint.get().y > barrier.bottom)
                {
                    nextDirection = DOWN;
                }
                else
                {
                    var best = abs(_endPoint.get().y - barrier.y) + abs(newStartPoint.y - barrier.y) + abs(inverse_trap._UpOrLeft - barrier.x) + abs(inverse_trap._UpOrLeft - _endPoint.get().x) >
                            abs(barrier.bottom - newStartPoint.y) + abs(barrier.bottom - _endPoint.get().y) + abs(inverse_trap._DownOrRight - barrier.x) + abs(inverse_trap._DownOrRight - _endPoint.get().x);
                    nextDirection = best ? DOWN : UP;
                }
            }
        }
//endregion
//region 检查最佳方向是否走的通（进一步逼近半开陷阱）
        if (UP == nextDirection)
        {
            if (trap._trap_type == TRAP_TYPE.TRAP_DOWNORRIGHT_EDGE)
            {
                newStartPoint.x = max(newStartPoint.x, trap._forward - BARRIER_SPACE);
            }
        }
        else
        {
            if (trap._trap_type == TRAP_TYPE.TRAP_UPORLEFT_EDGE)
            {
                newStartPoint.x = max(newStartPoint.x, trap._forward - BARRIER_SPACE);
            }
        }
//endregion 检查最佳方向是否走的桶（考虑平行障碍并进行坐标修正）

        // 下一步能走多远，需要估算一个值，进而判断是否存在“线冲突”,如果存在则调节当前点(当前的机制是缩短走行路径)
        newStartPoint = get_point_not_conincdence(RIGHT, newStartPoint, barrier, nextDirection, parallelBarrier, parallelInverse_trap);

        _route.add(newStartPoint);
        processPoints(newStartPoint, nextDirection, Tuple.of(barrier, inverse_trap), parallelBarrierTuple);

    }

    Point get_point_not_conincdence(AIDirection direction, Point newStartPoint, AIRectangle barrier, AIDirection nextDirection, AIRectangle parallelBarrier, TrapData parallelInverse_trap)
    {
        Point newStartPoint2 = newStartPoint;
        Point nextStepPoint = conjecture_next_step(direction, newStartPoint2, barrier, nextDirection, parallelBarrier, parallelInverse_trap);

        boolean bLoop = true;
        int max_loop = AIConstants.MAX_RETRY_TIMES;
        while (bLoop && max_loop-- >0)
        {
            Vec4d d4 = new Vec4d(newStartPoint2.x, newStartPoint2.y, nextStepPoint.x, nextStepPoint.y);
            boolean coin = check_conincdence(d4);
            if (coin)
            {
                if (barrier != null) {
                    if (LEFT == direction) { newStartPoint2.x += AIConstants.POINT_SPACE; nextStepPoint.x += AIConstants.POINT_SPACE;}
                    else if (UP == direction) { newStartPoint2.y += AIConstants.POINT_SPACE; nextStepPoint.y += AIConstants.POINT_SPACE;}
                    else if (RIGHT == direction) { newStartPoint2.x -= AIConstants.POINT_SPACE; nextStepPoint.x -= AIConstants.POINT_SPACE;}
                    else if (DOWN == direction) { newStartPoint2.y -= AIConstants.POINT_SPACE; nextStepPoint.y -= AIConstants.POINT_SPACE;}
                }
                else {
                    if (LEFT == direction) { newStartPoint2.x -= AIConstants.POINT_SPACE; nextStepPoint.x -= AIConstants.POINT_SPACE; }
                    else if (UP == direction) { newStartPoint2.y -= AIConstants.POINT_SPACE; nextStepPoint.y -= AIConstants.POINT_SPACE; }
                    else if (RIGHT == direction) { newStartPoint2.x += AIConstants.POINT_SPACE; nextStepPoint.x += AIConstants.POINT_SPACE; }
                    else if (DOWN == direction) { newStartPoint2.y += AIConstants.POINT_SPACE; nextStepPoint.y += AIConstants.POINT_SPACE; }
                }
            }
            else {
                bLoop = false;
            }
        }


        return newStartPoint2;
    }

    Point conjecture_next_step(AIDirection direction, Point newStartPoint, AIRectangle barrier, AIDirection nextDirection, AIRectangle parallelBarrier, TrapData parallelInverse_trap)
    {
        Point guess = newStartPoint;
        if (RIGHT == direction) {
            if (barrier != null) {
                if (UP == nextDirection) guess.y = min(barrier.y, _endPoint.get().y);
                else if (DOWN == nextDirection) guess.y = max(barrier.bottom, _endPoint.get().y);
            }
            else {
                if (parallelBarrier != null)
                {
                    if (UP == nextDirection) guess.y = min(_endPoint.get().y, parallelInverse_trap._DownOrRight);
                    else if (DOWN == nextDirection) guess.y = max(_endPoint.get().y, parallelInverse_trap._DownOrRight);
                }
                else {
                    guess.y = _endPoint.get().y;
                }
            }
        }
        else if (LEFT == direction) {
            if (barrier != null) {
                if (UP == nextDirection) guess.y = min(barrier.y, _endPoint.get().y);
                else if (DOWN == nextDirection) guess.y = max(barrier.bottom, _endPoint.get().y);
            }
            else {
                if (parallelBarrier != null)
                {
                    if (UP == nextDirection) guess.y = min(_endPoint.get().y, parallelInverse_trap._UpOrLeft);
                    else if (DOWN == nextDirection) guess.y = max(_endPoint.get().y, parallelInverse_trap._UpOrLeft);
                }
                else {
                    guess.y = _endPoint.get().y;
                }
            }
        }
        else if (UP == direction) {
            if (barrier != null) {
                if (LEFT == nextDirection) guess.x = min(barrier.x, _endPoint.get().x);
                else if (RIGHT == nextDirection) guess.x = max(barrier.right, _endPoint.get().x);
            }
            else {
                if (parallelBarrier != null)
                {
                    if (LEFT == nextDirection) guess.x = min(_endPoint.get().x, parallelInverse_trap._UpOrLeft);
                    else if (RIGHT == nextDirection) guess.x = max(_endPoint.get().x, parallelInverse_trap._UpOrLeft);
                }
                else {
                    guess.y = _endPoint.get().y;
                }
            }
        }
        else if (DOWN == direction) {
            if (barrier != null) {
                if (LEFT == nextDirection) guess.x = min(barrier.x, _endPoint.get().x);
                else if (RIGHT == nextDirection) guess.x = max(barrier.right, _endPoint.get().x);
            }
            else {
                if (parallelBarrier != null)
                {
                    if (LEFT == nextDirection) guess.x = min(_endPoint.get().x, parallelInverse_trap._DownOrRight);
                    else if (RIGHT == nextDirection) guess.x = max(_endPoint.get().x, parallelInverse_trap._DownOrRight);
                }
                else {
                    guess.y = _endPoint.get().y;
                }
            }
        }


        return guess;
    }

    boolean check_conincdence(Vec4d lineTocheck)
    {
        if (!_route.isEmpty())
        {
            Point prev = _route.get(0);
            for ( int i = 1; i < _route.size(); i++)
            {
                Point cur = _route.get(i);
                Vec4d d4 = new Vec4d(prev.x, prev.y, cur.x, cur.y);
                //if (Utils::line_conincdence(*d4,*lineTocheck))
                if (Utils.check_conincdence_by_distance(d4,lineTocheck))
                {
                    return true;
                }
                prev = cur;
            }
        }

        return false;
    }

    interface TrapProcessCallback {
        long trap_process(Point spPoint, AIDirection direction,
                          AIRectangle spBarrier_inflate, AIRectangle spFront, TrapData trap_data, TrapData inverse_trap_data);
    }

    /**
     * 用来计算陷阱的边缘。
     */
    class TrapCallback implements TrapProcessCallback {

        @Override
        public long trap_process(Point spPoint, AIDirection direction,
                                 AIRectangle spBarrier_inflate, AIRectangle spFront, TrapData trap_data, TrapData inverse_trap_data) {
            // 可以将本地参数带进来:std::shared_ptr<AIVector> vector, std::shared_ptr<POINT> spPoint
            // 修正 四边大小
            int left = spBarrier_inflate.x;
            int top = spBarrier_inflate.y;
            int right = spBarrier_inflate.x + spBarrier_inflate.width;
            int bottom = spBarrier_inflate.y + spBarrier_inflate.height;

            Rectangle cloneDst = null;
            try {
                _readWriteLock.readLock().lock();
                cloneDst = (Rectangle) _dstRect.get().clone();
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
                        trap_data.transformByMax(spFrontRight, Integer.MIN_VALUE);
                        // 条件保证增长是连续性的，不是跳跃的
                        if(spFrontRight >= inverse_trap_data._UpOrLeft) inverse_trap_data.transformByMin(spFront.x, Integer.MAX_VALUE);
                    }
                    else if (spFront.y > spPoint.y)	// 下重叠
                    {
                        trap_data.transformByMax(Integer.MIN_VALUE, spFrontRight);
                        if (spFrontRight >= inverse_trap_data._DownOrRight) inverse_trap_data.transformByMin(Integer.MAX_VALUE, spFront.x);
                    }
                    else if ( spFront.y < spPoint.y && spFrontBottom > spPoint.y)	//	考虑在穿过的情况：只反向需要
                    {
                        if (spFrontRight >= inverse_trap_data._UpOrLeft) inverse_trap_data.transformByMin(spFront.x, Integer.MAX_VALUE);
                        if (spFrontRight >= inverse_trap_data._DownOrRight) inverse_trap_data.transformByMin(Integer.MAX_VALUE, spFront.x);
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
                        trap_data.transformByMin(spFront.x, Integer.MAX_VALUE);
                        if (spFront.x <= inverse_trap_data._UpOrLeft) inverse_trap_data.transformByMax(spFrontRight, Integer.MIN_VALUE);
                    }
                    else if (spFront.y > spPoint.y)	// 下重叠
                    {
                        trap_data.transformByMin(Integer.MAX_VALUE, spFront.x);
                        if (spFront.x <= inverse_trap_data._DownOrRight) inverse_trap_data.transformByMax(Integer.MIN_VALUE, spFrontRight);
                    }
                    else if (spFront.y < spPoint.y &&  spPoint.y < spFrontBottom)
                    {
                        if (spFront.x <= inverse_trap_data._UpOrLeft) inverse_trap_data.transformByMax(spFrontRight, Integer.MIN_VALUE);
                        if (spFront.x <= inverse_trap_data._DownOrRight) inverse_trap_data.transformByMax(Integer.MIN_VALUE, spFrontRight);
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
                        trap_data.transformByMax(spFrontBottom, Integer.MIN_VALUE);
                        if (spFrontBottom >= inverse_trap_data._UpOrLeft) inverse_trap_data.transformByMin(spFront.y, Integer.MAX_VALUE);
                    }
                    else if (spFront.x > spPoint.x)	// 右重叠
                    {
                        trap_data.transformByMax(Integer.MIN_VALUE, spFrontBottom);
                        if (spFrontBottom >= inverse_trap_data._DownOrRight) inverse_trap_data.transformByMin(Integer.MAX_VALUE, spFront.y);
                    }
                    else if (spFront.x < spPoint.x && spPoint.x < spFrontRight)
                    {
                        if (spFrontBottom >= inverse_trap_data._UpOrLeft) inverse_trap_data.transformByMin(spFront.y, Integer.MAX_VALUE);
                        if (spFrontBottom >= inverse_trap_data._DownOrRight) inverse_trap_data.transformByMin(Integer.MAX_VALUE, spFront.y);
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
                        trap_data.transformByMin(spFront.y, Integer.MAX_VALUE);
                        if (spFront.y <= inverse_trap_data._UpOrLeft) inverse_trap_data.transformByMax(spFrontBottom, Integer.MIN_VALUE);
                    }
                    else if (spFront.x > spPoint.x)	// 右重叠
                    {
                        trap_data.transformByMin(Integer.MAX_VALUE, spFront.y);
                        if (spFront.y <= inverse_trap_data._DownOrRight) inverse_trap_data.transformByMax(Integer.MIN_VALUE, spFrontBottom);
                    }
                    else if (spFront.x < spPoint.x && spPoint.x < spFrontRight)
                    {
                        if (spFront.y <= inverse_trap_data._UpOrLeft) inverse_trap_data.transformByMax(spFrontBottom, Integer.MIN_VALUE);
                        if (spFront.y <= inverse_trap_data._DownOrRight) inverse_trap_data.transformByMax(Integer.MIN_VALUE, spFrontBottom);
                    }
                }
                case UNKOWN -> {

                }
            }

            spBarrier_inflate.setBounds(left, top, right-left, bottom-top);

            return 0;
        }
    }

    Triple<AIRectangle, TrapData, TrapData> barrier_find(Point spPoint, AIDirection vector, AIRectangle parallelBarrier){
        AIRectangle barrier = null;

        if (vector == LEFT) {
            barrier = barrier_left(spPoint, parallelBarrier);
        }
        else if (vector == RIGHT) {
            barrier = barrier_right(spPoint, parallelBarrier);
        }
        else if (vector == UP) {
            barrier = barrier_up(spPoint, parallelBarrier);
        }
        else if (vector == DOWN) {
            barrier = barrier_down(spPoint, parallelBarrier);
        }

        // 如果检测到的这个障碍和目标图元重叠了，那么不算障碍
        if (barrier != null)
        {
            if (true == barrier.intersects(_dstRect.get())) barrier = null;
        }
        // 如果存在障碍，那么检测是否存在陷阱
        TrapData trap_data = new TrapData(0);
        TrapData inverse_trap_data = new TrapData(0);
        AIRectangle full_growed = trap_find(barrier, vector, spPoint, trap_data, inverse_trap_data);

        // 根据方向进行不同的膨胀
        AIRectangle barrier_grow = null;
        if (barrier != null && full_growed != null)
        {
            if (vector == LEFT) {
                barrier_grow = new AIRectangle(barrier.get_table_id(), barrier.x, full_growed.y, barrier.right, full_growed.bottom);
            }
            else if (vector == RIGHT) {
                barrier_grow = new AIRectangle(barrier.get_table_id(), barrier.x, full_growed.y, barrier.right, full_growed.bottom);
            }
            else if (vector == UP) {
                barrier_grow = new AIRectangle(barrier.get_table_id(), full_growed.x, barrier.y, full_growed.right, barrier.bottom);
            }
            else if (vector == DOWN) {
                barrier_grow = new AIRectangle(barrier.get_table_id(), full_growed.x, barrier.y, full_growed.right, barrier.bottom);
            }
        }

        return Triple.of(barrier_grow, trap_data, inverse_trap_data);
    }

    /**
    功能：探测当前点左侧是否存在障碍<p>
    返回值：<p>
        不存在障碍返回 nullptr，存在障碍，返回障碍图元。
    */
    AIRectangle barrier_left(Point upPoint, AIRectangle parallelBarrier) {
        AIRectangle barrier = null;
        int left = min(_endPoint.get().x, parallelBarrier != null ? parallelBarrier.x:Integer.MAX_VALUE) - BARRIER_SPACE;

        ConcurrentHashMap<Integer, AIRectangle> mapI2Rect = AIManager.getInstance().get_mapI2Rect();
//region 循环所有图元，检查障碍
        for (var C : mapI2Rect.values())
        {
            if (C.equals(_srcRect.get()) || C.equals(_dstRect.get()))
            {
                continue;
            }

            if (C.y <= upPoint.y && upPoint.y <= C.bottom) {
                if (left < C.right && C.x < upPoint.x) {
                    // 如果左边存在障碍,是否更近
                    if (null == barrier) {
                        barrier = C;
                    }
                    else if ( C.right > barrier.right) {
                        barrier = C;
                    }
                }
            }
        }
//endregion

        return barrier;
    }

    AIRectangle barrier_right(Point upPoint, AIRectangle parallelBarrier) {
        AIRectangle barrier = null;
        int right = max(_endPoint.get().x, parallelBarrier!=null? parallelBarrier.right: Integer.MIN_VALUE) + BARRIER_SPACE;

        ConcurrentHashMap<Integer, AIRectangle> mapI2Rect = AIManager.getInstance().get_mapI2Rect();
//region 循环所有图元，检查障碍
        for (var C : mapI2Rect.values())
        {
            if (C.equals(_srcRect.get()) || C.equals(_dstRect.get()))
            {
                continue;
            }

            if (C.y <= upPoint.y && upPoint.y <= C.bottom) {
                if (upPoint.x< C.right && C.x< right) {
                    // 如果左边存在障碍,是否更近
                    if (null == barrier) {
                        barrier = C;
                    }
                    else if (C.x < barrier.x) {
                        barrier = C;
                    }
                }
            }
        }
//endregion

        return barrier;
    }

    AIRectangle barrier_up(Point upPoint, AIRectangle parallelBarrier) {
        AIRectangle barrier = null;
        int up = min(_endPoint.get().y, parallelBarrier != null? parallelBarrier.y: Integer.MAX_VALUE) - BARRIER_SPACE;

        ConcurrentHashMap<Integer, AIRectangle> mapI2Rect = AIManager.getInstance().get_mapI2Rect();
//region 循环所有图元，检查障碍
        for (var C : mapI2Rect.values())
        {
            if (C.equals(_srcRect.get()) || C.equals(_dstRect.get()))
            {
                continue;
            }

            if (C.x <= upPoint.x && upPoint.x <= C.right) {
                if (up < C.bottom && C.y < upPoint.y) {
                    // 如果上边存在障碍,是否更近
                    if (null == barrier) {
                        barrier = C;
                    }
                    else if (C.bottom > barrier.bottom) {
                        barrier = C;
                    }
                }
            }
        }
//endregion

        return barrier;
    }

    AIRectangle barrier_down(Point upPoint, AIRectangle parallelBarrier) {
        AIRectangle barrier = null;
        int down = max(_endPoint.get().y, parallelBarrier != null? parallelBarrier.bottom: Integer.MIN_VALUE)+ BARRIER_SPACE;

        ConcurrentHashMap<Integer, AIRectangle> mapI2Rect = AIManager.getInstance().get_mapI2Rect();
//region 循环所有图元，检查障碍
        for (var C : mapI2Rect.values())
        {
            if (C.equals(_srcRect.get()) || C.equals(_dstRect.get()))
            {
                continue;
            }

            if (C.x <= upPoint.x && upPoint.x <= C.right) {
                if (down > C.bottom && C.y > upPoint.y) {
                    // 如果左边存在障碍,是否更近
                    if (null == barrier) {
                        barrier = C;
                    }
                    else if (C.y < barrier.y) {
                        barrier = C;
                    }
                }
            }
        }
//endregion

        return barrier;
    }

    AIRectangle trap_find(AIRectangle spBarrier, AIDirection direction, Point spPoint, TrapData trap_data
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

        BiFunction<AIRectangle, AIRectangle, Long> functionCallback = (AIRectangle spBarrierInflate, AIRectangle spFront) -> {
            TrapCallback callback1 = new TrapCallback();
            return callback1.trap_process(spPoint, direction, spBarrierInflate, spFront, trap_data, inverse_trap_data);
        };
        int position = 1;	//	处理到第几个位置
        tranverse_overlap(spQueue, position, spBarrier_inflate,functionCallback);

        return spBarrier_inflate;
    }

    void tranverse_overlap(LinkedHashSet<AIRectangle> spQueue, int position, AIRectangle spBarrier_inflate,
                           BiFunction<AIRectangle, AIRectangle, Long> fnTrap)
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
