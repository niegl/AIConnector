package aiconnector.manager;

import aiconnector.connector.AIRectangle;
import lombok.NonNull;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 层管理对象
 */
public class AILayerManager extends ConcurrentHashMap<String, AIManagerItf> {

    /**
     * 静态内部类,包含一个静态属性：Singleton
     */
    private static class SingletonInstance {
        private static final AILayerManager INSTANCE = new AILayerManager();
    }

    /**
     * 对外公有的静态方法，直接返回SingletonInstance.INSTANCE
     */
    public static synchronized AILayerManager getInstance() {
        return SingletonInstance.INSTANCE;
    }

    /**
     * 获取指定layout名称的manager对象，每个画布应该对应一个manager。如果不存在该画布那么新建一个返回.
     * @param key manager对象名称
     * @return 画布对应manager
     */
    public AIManagerItf getManager(String key) {
        AIManagerItf orDefault = get(key);
        if (orDefault == null) {
            orDefault = new AIManager();
            put(key, orDefault);
        }

        return orDefault;
    }

}
