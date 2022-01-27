package aiconnector.connector;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 上下左右方向（从源点看目标点）
 */
@AllArgsConstructor
public enum AIDirection {
    DOWN(0, -1 ),			//下
    UP (0, 1),			//上
    LEFT ( -1, 0 ),		//左
	RIGHT (1, 0 ),		//右
	LEFT_UP (-1, 1 ),		//左上
	RIGHT_UP (1, 1 ),		//右上
	LEFT_DOWN (-1, -1 ),	//左下
	RIGHT_DOWN (1, -1 ),	//右下
	OVERLAP (0, 0 ),		//重叠
	UNKOWN (Integer.MAX_VALUE, Integer.MAX_VALUE); //

	@Getter
    private int x;
    @Getter
    private int y;

}
