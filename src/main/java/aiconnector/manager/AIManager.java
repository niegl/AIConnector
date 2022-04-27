package aiconnector.manager;

import aiconnector.connector.AIConnector;
import aiconnector.connector.AIRectangle;
import aiconnector.setting.AIConstants;
import aiconnector.utils.Tuple;
import lombok.Getter;
import lombok.NonNull;

import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

class AIManager implements AIManagerItf {
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
     * 所有的<b>图元</b>: 表hashCode-> 表图元
     */
    @Getter
    ConcurrentHashMap<Integer, AIRectangle> mapTableId2Rect = new ConcurrentHashMap<>();
    /**
     * 所有<b>连线</b>.connectorID-> AIConnector。connectorID的生成规则：Pair.with(hBox, hBox2).hashCode()
     */
    ConcurrentHashMap<Integer, AIConnector> mapLineId2Connector = new ConcurrentHashMap<>();
    /**
     * 图元->图元所有<b>连线关系</b>. 表id->表连线id. id的生成规则都是使用的视图对象。<p>
     * 表id的生成规则：formTemplate的hashcode;
     */
    ConcurrentHashMap<Integer, CopyOnWriteArrayList<Integer>> mapTableId2LineIDs = new ConcurrentHashMap<>();
    /**
     * 存储图元<b>冲突</b>: 图元-> 冲突图元
     */
    ConcurrentHashMap<Integer, CopyOnWriteArraySet<AIRectangle>> mapTableId2Overlaps = new ConcurrentHashMap<>();

    /**
     * 存储跟特定图元相关的连线<p>
     * 	参数：<p>
     * 	std::pair<LONG64,LONG64>：<源图元id、目标图元id><p>
     * 	std::vector<AIConnector>: 两个图元之间的连线，不同的连线用AIConnector：：m_uIdentify来区分
     * @return
     */
    @Override
    public CopyOnWriteArraySet<AIRectangle> getOverlap(Integer table_id) {
        return mapTableId2Overlaps.get(table_id);
    }

    /**
     * v2.0添加图元并建立图元关系
     * @param rectangle 图元
     * @return true：添加成功
     */
    @Override
    public boolean add_rect(@NonNull AIRectangle rectangle) {
        int table_id = rectangle.get_table_id();
        if (mapTableId2Rect.putIfAbsent(table_id,rectangle) == null) {
            // 建立图元关联
            return build_overlap(table_id);
        }

        return false;
    }

    /**
     * v2.0 删除图元并解除关系、删除连线
     * @param table_id 图元id
     * @return true：删除成功
     */
    @Override
    public boolean delete_rect(Integer table_id) {
        AIRectangle removed = mapTableId2Rect.get(table_id);
        if (removed != null) {
            // 删除连线
            CopyOnWriteArrayList<Integer> integers = mapTableId2LineIDs.get(table_id);
            if (integers != null) {
                integers.parallelStream().forEach(this::delete_line);
            }
            // 解除关系
            delete_overlap(table_id);
            // 先删除连线在删除图元
            mapTableId2Rect.remove(table_id);
        }

        return true;
    }

    /**
     * 搜索路径
     * @param src_table_id 源图元
     * @return 路径（可能为多个）
     */
    public List<Tuple<Integer, List<Point>>> searchRoute(int src_table_id)
    {
        CopyOnWriteArrayList<Integer> lines = get_connections(src_table_id);
        if (lines == null) {
            return null;
        }

        return lines.parallelStream().map(line -> {
            AIConnector aiConnector = mapLineId2Connector.get(line);
            if (aiConnector != null) {
                List<Point> route = aiConnector.search_route();
                if (!route.isEmpty()) {
                    return Tuple.of(aiConnector.get_connector_id(), route);
                }
            }
            return null;
        }).filter(Objects::nonNull).toList();
    }

//    Boolean refresh_overlap(int table_id, REFRESH_REASON refresh_type)
//    {
//        // 如果是新加入的图元，则直接建立关系
//        if (REFRESH_REASON.REFRESH_ADD_NEW == refresh_type)
//        {
//            attach_overlap(table_id);
//        }
//        else if (REFRESH_REASON.REFRESH_DELETE == refresh_type)
//        {
//            detach_overlap(table_id);
//        }
//        else if (REFRESH_REASON.REFRESH_MOVE == refresh_type)
//        {
//            detach_overlap(table_id);
//            attach_overlap(table_id);
//        }
//        else {
//            // 自动重分布
//
//        }
//
//        return false;
//    }

    @Override
    public Optional<AIRectangle> find_rect(Integer table_id) {
        return Optional.ofNullable(mapTableId2Rect.get(table_id)) ;
    }

    /**
     * v2.0
     * 功能：
     * 1、矩形添加后更新关系
     * 2、图元位置移动后更新关系
     * 3、图元删除后更新关系
     * 4、点击自动重分布后，更新图元位置/更新图元关系?
     * @param table_id
     * @return TRUE: 刷新成功
     */
    private boolean build_overlap(int table_id)    // 建立关系
    {
        var spTempRect = find_rect(table_id);
        if (spTempRect.isEmpty()) return false;

        // 循环当前所有图元，建立图元关系
        AIRectangle newRectangle = spTempRect.get();
        mapTableId2Rect.values().parallelStream().forEach(spExist -> {
            // 跳过自身
            if (spExist.equals(newRectangle)) return;
            // 如果图元重叠,则加入
            boolean boverlap =  spExist.intersects(new Rectangle(newRectangle.x - AIConstants.OVERLAP_SPACE, newRectangle.y - AIConstants.OVERLAP_SPACE, newRectangle.width+AIConstants.OVERLAP_SPACE, newRectangle.height+AIConstants.OVERLAP_SPACE));
            if (boverlap) {
                // 更新新图元的覆盖关系
                CopyOnWriteArraySet<AIRectangle> overlaps = mapTableId2Overlaps.getOrDefault(table_id, new CopyOnWriteArraySet<>());
                overlaps.add(spExist);
                mapTableId2Overlaps.put(table_id, overlaps);

                // 更新与新图元覆盖的 图元 的覆盖关系
                CopyOnWriteArraySet<AIRectangle> alreadyExist = mapTableId2Overlaps.getOrDefault(spExist.get_table_id(), new CopyOnWriteArraySet<>());
                alreadyExist.add(newRectangle);
                mapTableId2Overlaps.put(spExist.get_table_id(), alreadyExist);
            }
        });

        return true;
    }

    /**
     * v2.0 <p>
     * 解除table_id与其他图元的关系
     * @param table_id
     * @return
     */
    boolean delete_overlap(Integer table_id)
    {
        Optional<AIRectangle> spTempRect = find_rect(table_id);
        if (spTempRect.isEmpty()) return false;

        // 不存在重叠关系
        AIRectangle rectDelete = spTempRect.get();
        CopyOnWriteArraySet<AIRectangle> rc_overlaps = mapTableId2Overlaps.remove(table_id);
        if (rc_overlaps == null) {
            return false;
        }

        // 删除重叠关系
        rc_overlaps.parallelStream().forEach(overlap -> {
            int idByRect = overlap.get_table_id();
            CopyOnWriteArraySet<AIRectangle> suboverlap = mapTableId2Overlaps.get(idByRect);
            if (suboverlap != null) {
                suboverlap.remove(rectDelete);
                if (suboverlap.isEmpty()) {
                    mapTableId2Overlaps.remove(idByRect);
                }
            }
        });

        return true;
    }

    /**
     * v2.0
     * 功能：
     * 1、删除连线.如果删除连线后，图元不存在任何连线，那么删除图元相关连线。
     * 2、释放锚点
     * @param lineId 待删除连线id
     * @return
     */
    @Override
    public boolean delete_line(Integer lineId) {
        AIConnector remove = mapLineId2Connector.remove(lineId);
        // 连线不存在
        if (remove == null) return false;

        // 获取连线两端的图元，删除图元的这跟线
        AIRectangle srcRect = remove.get_srcRect();
        AIRectangle dstRect = remove.get_dstRect();

        return delete_line(lineId, srcRect, dstRect);
    }

    private boolean delete_line(Integer lineId, @NonNull AIRectangle srcRect, @NonNull AIRectangle dstRect) {
        CopyOnWriteArrayList<Integer> integersLine = mapTableId2LineIDs.get(srcRect.get_table_id());
        CopyOnWriteArrayList<Integer> integersLine1 = mapTableId2LineIDs.get(dstRect.get_table_id());

        boolean delete = true;
        if (integersLine != null) {
            delete = integersLine.remove(lineId);
            if (integersLine.isEmpty()) {
                mapTableId2LineIDs.remove(srcRect.get_table_id());
            }
        }
        if (integersLine1 != null) {
            delete &= integersLine1.remove(lineId);
            if (integersLine1.isEmpty()) {
                mapTableId2LineIDs.remove(dstRect.get_table_id());
            }
        }

        // 删除锚点
        delete_anchor(srcRect, dstRect, lineId);
        return delete;
    }

    // 功能：
    // 1、删除图元后解除与图元相关的连线
    // 2、移动图元后解除先前的连线
    // 3、释放锚点
    // 返回值：
    // TRUE: 成功
    // FALSE:
//    boolean delete_line(AIRectangle detachRect)    // 解除连线关系
//    {
//        if (null == detachRect)
//        {
//            return false;
//        }
//
//        int table_id = detachRect.get_table_id();
//        CopyOnWriteArrayList<Integer> lineIds = mapTableId2LineIDs.remove(table_id);
//
//        Tuple<Integer, Integer> searchKeys = mapTableId2LineIDs.searchKeys(2, new Function<Tuple<Integer, Integer>, Tuple<Integer, Integer>>() {
//            @Override
//            public Tuple<Integer, Integer> apply(Tuple<Integer, Integer> integerIntegerTuple) {
//                if (integerIntegerTuple.a.equals(table_id) || integerIntegerTuple.b.equals(table_id)) {
//                    return integerIntegerTuple;
//                }
//                return null;
//            }
//        });
//
//        if (searchKeys != null) {
//            // 释放锚点
//            mapTuple2Connection.get(searchKeys).parallelStream().forEach(aiConnector -> delete_line(searchKeys.a, searchKeys.b, aiConnector.get_connector_id()));
//            mapTuple2Connection.remove(searchKeys);
//        }
//
//        return true;
//    }

    /**
     * 绑定路径到图元，路径由使用者提供，保证路径id的唯一和持久一致；
     * @param spConnector
     * @return
     */
    @Override
    public boolean add_line(@NonNull AIConnector spConnector) {
        if (spConnector.get_srcRect() == null
                || spConnector.get_dstRect() == null) {
            return false;
        }

        int connectorId = spConnector.get_connector_id();
        mapLineId2Connector.put(connectorId, spConnector);

        // 添加到 表->连线
        int srcTableID = spConnector.get_srcRect().get_table_id();
        int dstTableID = spConnector.get_dstRect().get_table_id();
        CopyOnWriteArrayList<Integer> orDefault = mapTableId2LineIDs.getOrDefault(srcTableID, new CopyOnWriteArrayList<>());
        if (orDefault.isEmpty()) {
            mapTableId2LineIDs.put(srcTableID, orDefault);
        }
        orDefault.add(connectorId);
        // 如果源表和目标表是一个，那么添加一个即可。
        if (srcTableID == dstTableID) {
            return true;
        }
        CopyOnWriteArrayList<Integer> orDefault2 = mapTableId2LineIDs.getOrDefault(dstTableID, new CopyOnWriteArrayList<>());
        if (orDefault2.isEmpty()) {
            mapTableId2LineIDs.put(dstTableID, orDefault2);
        }
        orDefault2.add(connectorId);

        return true;
    }

    @Override
    public boolean add_line(@NonNull AIRectangle srcRect, @NonNull AIRectangle dstRect, @NonNull Integer lineId) {
        return add_line(new AIConnector(srcRect, dstRect, lineId, this));
    }

    /**
     * v2.0 删除连线
     * @param line_id 连线id
     * @param srcId 源图元
     * @param dstId 目标图元
     * @return
     */
    @Override
    public boolean delete_line(Integer line_id, int srcId, int dstId) {
        var spSrcRect = find_rect(srcId);
        var spDstRect = find_rect(dstId);
        if (spSrcRect.isEmpty() || spDstRect.isEmpty()) {
            return false;
        }

        return delete_line(line_id, spSrcRect.get(), spDstRect.get());
    }

    /**
     * v2.0 获取与图元相关的连线
     * @param table_id 图元id
     * @return 连线列表
     */
    @Override
    public CopyOnWriteArrayList<Integer> get_connections(Integer table_id) {
        return mapTableId2LineIDs.get(table_id);
    }

    @Override
    public AIConnector get_connection(Integer connection_id) {
        return mapLineId2Connector.get(connection_id);
    }

    @Override
    public void move_rect(Integer table_id, @NonNull Rectangle lpRect) {
        Optional<AIRectangle> spTempRect = find_rect(table_id);
        if (spTempRect.isEmpty()) return;

 		// 删除图元
 		delete_rect(table_id);
 		// 添加图元
 		add_rect(new AIRectangle(lpRect, table_id));
    }

    @Override
    public boolean attach_anchor(AIRectangle spSrcRect, Point point, int connectorID) {
        spSrcRect.insert_or_update_anchor_point(point, connectorID);
        return true;
    }

    /**
     * v2.0 删除锚点。
     * @param spSrcRect
     * @param spDstRect
     * @param lineId 与lineId关联的锚点
     * @return
     */
    boolean delete_anchor(@NonNull AIRectangle spSrcRect, @NonNull AIRectangle spDstRect, Integer lineId) {
        spSrcRect.detach_anchor_point(lineId);
        spDstRect.detach_anchor_point(lineId);

        return true;
    }

}
