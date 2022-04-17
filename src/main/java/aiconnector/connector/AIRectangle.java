package aiconnector.connector;

import aiconnector.setting.AIConstants;
import aiconnector.utils.tuple.Tuple;
import lombok.Getter;
import lombok.Setter;

import java.awt.*;
import java.util.Collection;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static aiconnector.connector.AIDirection.*;
import static aiconnector.connector.AIDirection.RIGHT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class AIRectangle extends Rectangle {
    /**
     * 使用java自带的log工具
     */
    private static final Logger logger = Logger.getLogger(AIRectangle.class.getName());
    static {
        logger.setLevel(WARNING);
    }
    @Getter
    @Setter
    private int _table_id;//引用类型

    public AIRectangle(Rectangle r, int _table_id) {
        super(r);
        this._table_id = _table_id;
    }

    private AIRectangle(Rectangle r) {
        super(r);
    }

    public AIRectangle(int x, int y, int width, int height, int _table_id) {
        super(x, y, width, height);
        this._table_id = _table_id;
    }

    public int right = this.x + this.width;
    public int bottom = this.y + this.height;

    /**
     * connectorID->锚点集合
     */
    @Getter
    private ConcurrentHashMap<Integer, Point> _anchorLine2Point = new ConcurrentHashMap<>();

    /**
     * Params:
     * key – key with which the specified value is to be associated
     * value – value to be associated with the specified key
     * Returns:
     * the previous value associated with key, or null if there was no mapping for key
     */
    public Point insert_or_update_anchor_point(Point point, int lineID) {
        logger.info("attach point: " + point);
        return _anchorLine2Point.put(lineID,point);
    }

    /**
     * Removes the key (and its corresponding value) from this map. This method does nothing if the key is not in the map.
     * Params:
     * key – the key that needs to be removed
     * Returns:
     * the previous value associated with key, or null if there was no mapping for key
     */
    public Point detach_anchor_point(int lineID) {
        logger.info("detach point: " + _anchorLine2Point.get(lineID));
        return _anchorLine2Point.remove(lineID);
    }

    public final Tuple<Point, AIDirection> get_free_anchor(int connectorID, Point startPoint, AIDirection forward_direction, boolean bInvertPeek)
    {
        Point clonePoint = (Point) startPoint.clone();
        Tuple<Point, AIDirection> guideAnchor = get_next_anchor(connectorID, clonePoint, forward_direction, bInvertPeek);

        return guideAnchor;
    }

    private Tuple<Point, AIDirection> get_next_anchor(int connectorID, Point startPoint, AIDirection forward_direction, boolean bInvertPeek/*逆时针方向为true*/)
    {
        AIDirection next_direction = forward_direction;

        if (check_anchor_available(connectorID, startPoint)) {
            return Tuple.of(startPoint, next_direction);
        }

        switch (forward_direction)
        {
            case LEFT:
                if (!bInvertPeek)
                {
                    startPoint.y -= AIConstants.POINT_SPACE;
                    if (startPoint.y < this.y + AIConstants.POINT2EDGE_GAP) {
                        next_direction = UP;
                        startPoint.setLocation(this.x + AIConstants.POINT2EDGE_GAP, this.y);
                    }
                }
                else {
                    startPoint.y += AIConstants.POINT_SPACE;
                    if (startPoint.y > this.bottom - AIConstants.POINT2EDGE_GAP) {
                        next_direction = DOWN;
                        startPoint.setLocation( this.x + AIConstants.POINT2EDGE_GAP, this.bottom);
                    }
                }
                break;
            case UP:
                if (!bInvertPeek)
                {
                    startPoint.x += AIConstants.POINT_SPACE;
                    if (startPoint.x > this.right - AIConstants.POINT2EDGE_GAP) {
                        next_direction = RIGHT;
                        startPoint.setLocation( this.right, this.getY() + AIConstants.POINT2EDGE_GAP);
                    }
                }
                else {
                    startPoint.x -= AIConstants.POINT_SPACE;
                    if (startPoint.x < this.x + AIConstants.POINT2EDGE_GAP) {
                        next_direction = LEFT;
                        startPoint.setLocation( this.x, this.y + AIConstants.POINT2EDGE_GAP );
                    }
                }
                break;
            case RIGHT:
                if (!bInvertPeek)
                {
                    startPoint.y += AIConstants.POINT_SPACE;
                    if (startPoint.y > this.bottom - AIConstants.POINT2EDGE_GAP) {
                        next_direction = DOWN;
                        startPoint.setLocation( this.right - AIConstants.POINT2EDGE_GAP, this.bottom);
                    }
                }
                else {
                    startPoint.y -= AIConstants.POINT_SPACE;
                    if (startPoint.y < this.y + AIConstants.POINT2EDGE_GAP) {
                        next_direction = UP;
                        startPoint.setLocation( this.right - AIConstants.POINT2EDGE_GAP, this.y);
                    }
                }
                break;
            case DOWN:
                if (!bInvertPeek)
                {
                    startPoint.x -= AIConstants.POINT_SPACE;
                    if (startPoint.x < this.x + AIConstants.POINT2EDGE_GAP) {
                        next_direction = LEFT;
                        startPoint.setLocation( this.x, this.bottom - AIConstants.POINT2EDGE_GAP);
                    }
                }
                else {
                    startPoint.x += AIConstants.POINT_SPACE;
                    if (startPoint.x > this.right - AIConstants.POINT2EDGE_GAP) {
                        next_direction = RIGHT;
                        startPoint.setLocation(this.right, this.bottom - AIConstants.POINT2EDGE_GAP);
                    }
                }
                break;
            default:
                break;
        }

        return get_next_anchor(connectorID, startPoint, next_direction, bInvertPeek);
    }

    /**
     * 增加同一条路径检查，允许返回和当前同样的锚点，解决画线时上下跳动的问题。
     */
    public boolean check_anchor_available(int uLineIndentify, Point anchorPoint)
    {
        Point point = _anchorLine2Point.get(uLineIndentify);
        if (point != null) {
            if (point.equals(anchorPoint)) {
                return true;
            }
        }

        Collection<Point> pointCollection = _anchorLine2Point.values();
        return !pointCollection.contains(anchorPoint);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AIRectangle that = (AIRectangle) o;
        return _table_id == that._table_id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), _table_id);
    }
}
