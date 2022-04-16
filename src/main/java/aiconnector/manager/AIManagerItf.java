package aiconnector.manager;

import aiconnector.connector.AIConnector;
import aiconnector.connector.AIRectangle;
import lombok.NonNull;

import java.awt.*;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public interface AIManagerItf {
    boolean add_rect(@NonNull AIRectangle rectangle);
    boolean delete_rect(Integer table_id);

    Optional<AIRectangle> find_rect(Integer table_id);

    boolean add_line(@NonNull AIConnector spConnector);
    boolean delete_line(Integer line_id, int srcId, int dstId);

    CopyOnWriteArrayList<Integer> get_connections(Integer table_id);
    AIConnector get_connection(Integer connection_id);

    void move_rect(Integer table_id, @NonNull Rectangle lpRect);
}
