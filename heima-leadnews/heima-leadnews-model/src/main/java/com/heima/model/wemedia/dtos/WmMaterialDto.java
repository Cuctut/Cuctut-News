package com.heima.model.wemedia.dtos;

import com.heima.model.common.dtos.PageRequestDto;
import lombok.Data;

@Data
public class WmMaterialDto extends PageRequestDto {

    /**
     * 是否是查询收藏List请求
     * 1收藏 0全部
     */
    private Short isCollection;
}
