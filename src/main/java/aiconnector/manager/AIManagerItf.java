package aiconnector.manager;

import aiconnector.connector.AIConnector;
import aiconnector.connector.AIRectangle;

import java.awt.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

public interface AIManagerItf {

    boolean add_rect(int table_id, Rectangle lpSrcRect);
    boolean delete_rect(int table_id);
    void move_rect(int table_id, Rectangle lpRect);
    // 功能：
    // 1、删除连线
    // 返回值：
    // TRUE: 成功
    // FALSE:
    boolean delete_line(int srcId, int dstId, int line_id);

    CopyOnWriteArrayList<AIConnector> get_connection(int src_table_id, int dest_table_id);


}
