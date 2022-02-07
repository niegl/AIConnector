package aiconnector.manager;

import aiconnector.connector.AIConnector;
import aiconnector.connector.AIRectangle;
import aiconnector.utils.tuple.Tuple;
import lombok.Getter;

import java.awt.*;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;

public class AIManager implements AIManagerItf {
    private AIManager() {}
    //静态内部类,包含一个静态属性：Singleton
    private static class SingletonInstance {
        private static final AIManager INSTANCE = new AIManager();
    }
    //对外公有的静态方法，直接返回SingletonInstance.INSTANCE
    public static synchronized AIManager getInstance() {
        return SingletonInstance.INSTANCE;
    }
    /**
     * 图元操作类型枚举类
     */
    enum REFRESH_REASON
    {
        REFRESH_ADD_NEW,	// 添加新图元后更新重叠关系
        REFRESH_MOVE,		// 移动现有图元后更新现有图元关系
        REFRESH_DELETE,
        REFRESH_AUTO_LAYOUT // 自动重分布后更新图元关系
    };

    /**
     * 根据存放顺序,存储所有的图元
     */
    CopyOnWriteArrayList<Rectangle> m_rects = new CopyOnWriteArrayList<>();
    /**
     * 方便进行图元的查询
     */
    @Getter
    ConcurrentHashMap<Integer, AIRectangle> _mapI2Rect = new ConcurrentHashMap<>();
    /**
     * 存储图元冲突
     */
    ConcurrentHashMap<Integer, CopyOnWriteArraySet<AIRectangle>> _mapI2RectOverlap = new ConcurrentHashMap<>();
    /**
     * 存储跟特定图元相关的连线<p>
     * 	参数：<p>
     * 	std::pair<LONG64,LONG64>：<源图元id、目标图元id><p>
     * 	std::vector<AIConnector>: 两个图元之间的连线，不同的连线用AIConnector：：m_uIdentify来区分
     */
    ConcurrentHashMap<Tuple<Integer,Integer>, CopyOnWriteArrayList<AIConnector>> _mapTuple2Connection = new ConcurrentHashMap<>();

    public CopyOnWriteArraySet<AIRectangle> getOverlap(int table_id) {
        return _mapI2RectOverlap.get(table_id);
    }

    @Override
    public boolean add_rect(int table_id, Rectangle lpSrcRect) {
        Objects.requireNonNull(lpSrcRect);

        if (_mapI2Rect.putIfAbsent(table_id,new AIRectangle(lpSrcRect,table_id)) == null) {
            // 建立图元关联
            return refresh_overlap(table_id, REFRESH_REASON.REFRESH_ADD_NEW);
        }

        return false;
    }

    @Override
    public boolean delete_rect(int table_id) {
        AIRectangle removed = _mapI2Rect.remove(table_id);
        if (removed != null) {
            // 解除关系
            detach_overlap(table_id);
            // 删除连线
            detach_line(removed);
        }

        return true;
    }

    Boolean refresh_overlap(int table_id, REFRESH_REASON refresh_type)
    {
        // 如果是新加入的图元，则直接建立关系
        if (REFRESH_REASON.REFRESH_ADD_NEW == refresh_type)
        {
            attach_overlap(table_id);
        }
        else if (REFRESH_REASON.REFRESH_DELETE == refresh_type)
        {
            detach_overlap(table_id);
        }
        else if (REFRESH_REASON.REFRESH_MOVE == refresh_type)
        {
            detach_overlap(table_id);
            attach_overlap(table_id);
        }
        else {
            // 自动重分布

        }

        return false;
    }

    AIRectangle find_rect(int table_id) {        return _mapI2Rect.get(table_id);
    }

    // 功能：
    // 1、矩形添加后更新关系
    // 2、图元位置移动后更新关系
    // 3、图元删除后更新关系
    // 4、点击自动重分布后，更新图元位置/更新图元关系?
    // 返回值：
    // TRUE: 刷新成功
    // FALSE:
    //BOOL refresh_overlap(Rectangle spTempRect, REFRESH_REASON refresh_type);
    boolean attach_overlap(int table_id)    // 建立关系
    {
        var spTempRect = find_rect(table_id);
        if (spTempRect == null) return true;

        // 循环当前所有图元，建立图元关系
        for ( AIRectangle spExist : _mapI2Rect.values())
        {
            // 跳过自身
            if (spExist.equals(spTempRect)) continue;
            // 如果图元重叠,则加入
            boolean boverlap =  spExist.intersects(spTempRect);
            if (boverlap)
            {
                CopyOnWriteArraySet<AIRectangle> overlaps = _mapI2RectOverlap.getOrDefault(table_id, new CopyOnWriteArraySet<>());
                overlaps.add(spExist);
                _mapI2RectOverlap.put(table_id,overlaps);
            }
        }

        return true;
    }

    /**
     * 解除table_id与其他图元的关系
     * @param table_id
     * @return
     */
    boolean detach_overlap(int table_id)
    {
        Rectangle spTempRect = find_rect(table_id);
        if (spTempRect == null) return true;

        // 删除重叠关系
        CopyOnWriteArraySet<AIRectangle> rc_overlaps = _mapI2RectOverlap.get(table_id);
        if (rc_overlaps != null) {
            // 首先解除与他有关系的图元、与他的关系
            for (AIRectangle overlap : rc_overlaps)
            {
                int idByRect = overlap.get_table_id();
                CopyOnWriteArraySet<AIRectangle> suboverlap = _mapI2RectOverlap.get(idByRect);
                if (suboverlap == null) {
                    continue;
                }

                suboverlap.remove(spTempRect);
                if (suboverlap.isEmpty()) {
                    _mapI2RectOverlap.remove(idByRect);
                }
            }
            // 最后解除他的关系
            _mapI2RectOverlap.remove(table_id);
        }

        return true;
    }

    // 功能：
    // 1、删除图元后解除与图元相关的连线
    // 2、移动图元后解除先前的连线
    // 返回值：
    // TRUE: 成功
    // FALSE:
    boolean detach_line(AIRectangle detachRect)    // 解除连线关系
    {
        if (null == detachRect)
        {
            return false;
        }

        int table_id = detachRect.get_table_id();
        Tuple<Integer, Integer> searchKeys = _mapTuple2Connection.searchKeys(2, new Function<Tuple<Integer, Integer>, Tuple<Integer, Integer>>() {
            @Override
            public Tuple<Integer, Integer> apply(Tuple<Integer, Integer> integerIntegerTuple) {
                if (integerIntegerTuple.a.equals(table_id) || integerIntegerTuple.b.equals(table_id)) {
                    return integerIntegerTuple;
                }
                return null;
            }
        });

        if (searchKeys != null) {
            _mapTuple2Connection.remove(searchKeys);
        }

        return true;
    }

    // 功能：
    // 1、建立连线路径后将其绑定图元
    // 2、移动图元后绑定将新路径绑定到图元（情况1的延伸）
    // 返回值：
    // TRUE: 成功
    // FALSE:
    boolean attach_line(AIRectangle spSrcRect, AIRectangle spDstRect, AIConnector spConnector) {
        int srcTableId = spSrcRect.get_table_id();
        int dstTableId = spDstRect.get_table_id();

        CopyOnWriteArrayList<AIConnector> connectors  = get_connection(srcTableId, dstTableId);
        if (connectors != null)
        {
            connectors.add(spConnector);
        }
        else {
            _mapTuple2Connection.put(Tuple.of(srcTableId,dstTableId), new CopyOnWriteArrayList<AIConnector>(Collections.singleton(spConnector)));
        }

        return true;
    }

    // 功能：
    // 1、删除连线
    // 返回值：
    // TRUE: 成功
    // FALSE:
    @Override
    public boolean delete_line(int srcId, int dstId, int line_id) {

        var spSrcRect = find_rect(srcId);
        var spDstRect = find_rect(dstId);

        CopyOnWriteArrayList<AIConnector> connectors = get_connection(srcId, dstId);
        if (connectors != null) {
            for (AIConnector conn :
                    connectors) {
                if (conn.get_connector_id() == line_id) {
                    delete_anchor(spSrcRect,spDstRect,conn);
                    connectors.remove(conn);
                    break;
                }
            }

            if (connectors.isEmpty()) {
                _mapTuple2Connection.remove(Tuple.of(srcId,dstId));
                _mapTuple2Connection.remove(Tuple.of(dstId,srcId));
            }
        }

        return true;
    }

    @Override
    public CopyOnWriteArrayList<AIConnector> get_connection(int src_table_id, int dest_table_id) {

        CopyOnWriteArrayList<AIConnector> aiConnectors = _mapTuple2Connection.get(Tuple.of(src_table_id, dest_table_id));
        if (aiConnectors != null) {
            return aiConnectors;
        }

        aiConnectors = _mapTuple2Connection.get(Tuple.of(dest_table_id, src_table_id));
        return aiConnectors;
    }

    @Override
    public void move_rect(int table_id, Rectangle lpRect)
    {
        Rectangle spTempRect = find_rect(table_id);
        if (spTempRect == null) return;

        spTempRect.setBounds(lpRect.x, lpRect.y, lpRect.width, lpRect.height);

        // 刷新图元与其他图元的关系
        refresh_overlap(table_id, REFRESH_REASON.REFRESH_MOVE);
// 		// 删除图元
// 		delete_rect(table_id);
// 		// 添加图元
// 		add_rect(table_id, lpRect);

    }

    boolean attach_anchor(AIRectangle spSrcRect, AIRectangle spDstRect, AIConnector connector) {
        CopyOnWriteArrayList<Point> route = connector.get_route();
        if (!route.isEmpty() && route.size() >= 2)
        {
            spSrcRect.insert_or_update_anchor_point(route.get(0), connector.get_connector_id());
            spDstRect.insert_or_update_anchor_point(route.get(route.size()-1), connector.get_connector_id());
        }
        return true;
    }

    boolean delete_anchor(AIRectangle spSrcRect, AIRectangle spDstRect, AIConnector connector) {
        Objects.requireNonNull(spSrcRect);
        Objects.requireNonNull(spDstRect);
        Objects.requireNonNull(connector);

        spSrcRect.detach_anchor_point(connector.get_connector_id());
        spDstRect.detach_anchor_point(connector.get_connector_id());

        return true;
    }

}
