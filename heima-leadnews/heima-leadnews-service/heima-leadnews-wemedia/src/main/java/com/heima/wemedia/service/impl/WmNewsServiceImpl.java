package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.common.constants.WemediaConstants;
import com.heima.common.exception.CustomException;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmNewsMaterial;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmNewsMaterialMapper;
import com.heima.wemedia.service.WmNewsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class WmNewsServiceImpl extends ServiceImpl<WmNewsMapper, WmNews> implements WmNewsService {

    @Autowired
    private WmNewsMaterialMapper wmNewsMaterialMapper;

    @Autowired
    private WmMaterialMapper wmMaterialMapper;

    /**
     * 条件查询文章列表
     * @param dto
     * @return
     */
    @Override
    public ResponseResult findList(WmNewsPageReqDto dto) {
        //1. 检查参数
        dto.checkParam();

        //2. 分页查询
        IPage<WmNews> page = new Page<>(dto.getPage(), dto.getSize());
        LambdaQueryWrapper<WmNews> wrapper = new LambdaQueryWrapper();
        //2.1 状态精确查询
        if (dto.getStatus()!=null){
            wrapper.eq(WmNews::getStatus, dto.getStatus());
        }
        //2.2 频道精确查询
        if (dto.getChannelId()!=null){
            wrapper.eq(WmNews::getChannelId, dto.getChannelId());
        }
        //2.3 时间范围查询
        if (dto.getBeginPubDate()!=null && dto.getEndPubDate()!=null){
            wrapper.between(WmNews::getCreatedTime, dto.getBeginPubDate(), dto.getEndPubDate());
        }
        //2.4 关键字模糊查询
        if (StringUtils.isNotBlank(dto.getKeyword())){
            wrapper.like(WmNews::getTitle, dto.getKeyword());
        }
        //2.5 确定登录人
        wrapper.eq(WmNews::getUserId, WmThreadLocalUtil.getUser().getId());
        //2.6 发布时间倒序
        wrapper.orderByDesc(WmNews::getCreatedTime);
        page = page(page, wrapper);
        //3.返回结果
        ResponseResult result = new PageResponseResult(dto.getPage(), dto.getSize(), (int) page.getTotal());
        result.setData(page.getRecords());
        return result;
    }

    /**
     * 发布修改文章或保存为草稿
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult submitNews(WmNewsDto dto) {
        //0. 校验参数
        if(dto==null || dto.getContent() == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        //1. 保存或修改文章
        WmNews wmNews = new WmNews();
        //1.1 dto 的属性内容拷贝到 wmNews，按属性名和类型匹配
        BeanUtils.copyProperties(dto, wmNews);
        //1.2 dto中的 List<String> images 复制到 wmNews 中的 String images
        if (dto.getImages() != null && dto.getImages().size() > 0) {
            wmNews.setImages(StringUtils.join(dto.getImages(), ","));
        }
        //1.3 数据库中的 type 是 unsigned 的，无法接受 wmNews 和 dto 中的 -1
        if (dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)){
            wmNews.setType(null);
        }
        //1.4 保存或修改文章
        saveOrUpdateWmNews(wmNews);
        //2. 判断是否为草稿，如果为草稿结束当前方法
        if (dto.getStatus().equals(WmNews.Status.NORMAL.getCode())){
            return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
        }

        //3. 不是草稿，保存文章内容图片和素材的关系
        //3.1 获取content中的图片信息
        List<String> materials = extractUrlInfo(dto.getContent());
        saveRelativeInfoforContent(materials, wmNews.getId());
        //4. 不是草稿，保存文章封面图片和素材的关系
        saveRelativeInfoforCover(materials, wmNews, dto);
        //5. 返回结果
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    /**
     * 处理文章封面中的图片和素材的关系
     * 如果当前封面类型为自动，则设置封面类型的数据
     * 匹配规则：
     * 1. 如果内容图片大于等于1，小于3 单图 type 1
     * 2，如果内容图片大于等于3多图 type 3
     * 3，如果内容没有图片，无图 type 0
     * @param materials
     * @param wmNews
     * @param dto
     */
    private void saveRelativeInfoforCover(List<String> materials, WmNews wmNews, WmNewsDto dto) {
        List<String> images = dto.getImages();

        //如果当前封面类型为自动，则设置封面类型的数据
        if (dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)){
            log.info("materials size: {}", materials.size());
            if (materials.size()>=3){
                //多图
                wmNews.setType(WemediaConstants.WM_NEWS_MANY_IMAGE);
                images = materials.stream().limit(3).collect(Collectors.toList());
            } else if (materials.size() >= 1) {
                //单图
                wmNews.setType(WemediaConstants.WM_NEWS_SINGLE_IMAGE);
                images = materials.stream().limit(1).collect(Collectors.toList());
            } else {
                //无图
                wmNews.setType(WemediaConstants.WM_NEWS_NONE_IMAGE);
            }

            if (images!=null && !images.isEmpty()){
                log.info("images size: {}", images.size());
                wmNews.setImages(StringUtils.join(images, ","));
                log.info("wmNews setImages successfully");
            }
            updateById(wmNews);
        }
        //处理文章封面中的图片和素材的关系
        if (images!=null && !images.isEmpty()){
            saveRelativeInfo(images, wmNews.getId(), WemediaConstants.WM_COVER_REFERENCE);
        }
    }

    /**
     * 处理文章内容中的图片和素材的关系
     *
     *
     * @param materials
     * @param id
     */
    private void saveRelativeInfoforContent(List<String> materials, Integer id) {
        saveRelativeInfo(materials, id, WemediaConstants.WM_CONTENT_REFERENCE);
    }

    /**
     * 根据 wmContentReference 判断保存文章的内容图片or封面图片的关系到数据库
     * @param materials
     * @param id
     * @param wmContentReference
     */
    private void saveRelativeInfo(List<String> materials, Integer id, Short wmContentReference) {
        if (materials!=null && !materials.isEmpty()) {
            //获取需要的参数
            List<WmMaterial> dbMaterial = wmMaterialMapper.selectList(Wrappers.<WmMaterial>lambdaQuery().in(WmMaterial::getUrl, materials));
            log.info("dbMaterial size: " + dbMaterial.size());
            if (dbMaterial == null || dbMaterial.size() == 0) {
                log.info("1");
                throw new CustomException(AppHttpCodeEnum.MATERIALS_REFERENCE_FAIL);
            }
            if (dbMaterial.size() != materials.size()) {
                log.info("2");
                throw new CustomException(AppHttpCodeEnum.MATERIALS_REFERENCE_FAIL);
            }
            List<Integer> idList = dbMaterial.stream().map(WmMaterial::getId).collect(Collectors.toList());
            //批量保存
            wmNewsMaterialMapper.saveRelations(idList, id, wmContentReference);
        }
    }

    /**
     * 提取文章内容中的图片、视频Url
     * @param content
     * @return
     */
    private List<String> extractUrlInfo(String content) {
        List<String> result = new ArrayList<>();
        List<Map> maps = JSON.parseArray(content, Map.class);
        for (Map map : maps) {
            if (map.get("type").equals("image")) {
                String url = (String) map.get("value");
                result.add(url);
            }
        }
        return result;
    }

    /**
     * 保存或修改文章
     * @param wmNews
     */
    private void saveOrUpdateWmNews(WmNews wmNews) {
        //补全属性
        wmNews.setCreatedTime(new Date());
        wmNews.setSubmitedTime(new Date());
        wmNews.setUserId(WmThreadLocalUtil.getUser().getId());
        wmNews.setEnable((short)1);
        //判断保存或修改操作
        if (wmNews.getId()==null){
            //新增保存操作
            save(wmNews);
        } else {
            //删除文章图片和素材的关系
            wmNewsMaterialMapper.delete(Wrappers.<WmNewsMaterial>lambdaQuery().eq(WmNewsMaterial::getNewsId, wmNews.getId()));
            //修改
            updateById(wmNews);
        }

    }
}
