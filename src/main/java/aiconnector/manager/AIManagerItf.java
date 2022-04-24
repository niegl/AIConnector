package aiconnector.manager;

import aiconnector.connector.AIConnector;
import aiconnector.connector.AIRectangle;
import lombok.NonNull;

import java.awt.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

public interface AIManagerItf {
    CopyOnWriteArraySet<AIRectangle> getOverlap(Integer table_id);

    boolean add_rect(@NonNull AIRectangle rectangle);
    boolean delete_rect(Integer table_id);

    Optional<AIRectangle> find_rect(Integer table_id);

    boolean add_line(@NonNull AIConnector spConnector);
    boolean add_line(@NonNull AIRectangle srcRect, @NonNull AIRectangle dstRect, @NonNull Integer lineId);
    boolean delete_line(Integer lineId);
    boolean delete_line(Integer line_id, int srcId, int dstId);

    CopyOnWriteArrayList<Integer> get_connections(Integer table_id);
    AIConnector get_connection(Integer connection_id);

    void move_rect(Integer table_id, @NonNull Rectangle lpRect);

    boolean attach_anchor(AIRectangle spSrcRect, Point point, int connectorID);

    ConcurrentHashMap<Integer, AIRectangle> getMapTableId2Rect();
}
