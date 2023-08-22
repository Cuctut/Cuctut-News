package com.heima.wemedia.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.file.service.FileStorageService;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmMaterialDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.service.WmMaterialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class WmMaterialServiceImpl extends ServiceImpl<WmMaterialMapper, WmMaterial> implements WmMaterialService {

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * 查询已上传素材List
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult findList(WmMaterialDto dto) {
        //1. 校验参数
        if (dto==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        } else {
            dto.checkParam();
        }
        //2. 分页查询
        IPage page = new Page(dto.getPage(), dto.getSize());
        LambdaQueryWrapper<WmMaterial> wrapper = new LambdaQueryWrapper<>();
        //2.1 是否收藏(否则查询全部)
        if (dto.getIsCollection() != null && dto.getIsCollection() == 1){
                wrapper.eq(WmMaterial::getIsCollection, dto.getIsCollection());
        }
        //2.2 按照用户查询
        wrapper.eq(WmMaterial::getUserId, WmThreadLocalUtil.getUser().getId());
        //2.3 按时间倒序查询
        wrapper.orderByDesc(WmMaterial::getCreatedTime);
        page = page(page, wrapper);

        //3. 返回结果
        ResponseResult result = new PageResponseResult(dto.getPage(), dto.getSize(), (int) page.getTotal());
        result.setData(page.getRecords());
        return result;
    }

    /**
     * 图片上传
     *
     * @param multipartFile
     * @return
     */
    @Override
    public ResponseResult uploadPicture(MultipartFile multipartFile) {
        //1. 检查参数
        if (multipartFile == null || multipartFile.getSize() == 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2. 上传图片到 minio 中
        String path = null;
        String newFilename = UUID.randomUUID().toString().replace("-", "");
        String postfix = null;
        String originalFilename = multipartFile.getOriginalFilename();
        if(originalFilename != null){
            postfix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        newFilename += postfix;

        try {
            path = fileStorageService.uploadImgFile("", newFilename, multipartFile.getInputStream());
            log.info("Upload successfully, path: {}", path);
        } catch (IOException e) {
            log.error("Upload failed");
            throw new RuntimeException(e);
        }
        //3. 保存图片到数据库中
        WmMaterial wmMaterial = new WmMaterial();
        wmMaterial.setUserId(WmThreadLocalUtil.getUser().getId());
        wmMaterial.setUrl(path);
        wmMaterial.setIsCollection((short) 0);
        wmMaterial.setType((short) 0);
        wmMaterial.setCreatedTime(new Date());
        save(wmMaterial);
        //4. 返回结果
        return ResponseResult.okResult(wmMaterial);
    }
}
