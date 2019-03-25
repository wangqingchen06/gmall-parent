package com.atguigu.gmall.pms.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.constant.RedisCacheConstant;
import com.atguigu.gmall.pms.entity.ProductCategory;
import com.atguigu.gmall.pms.mapper.ProductCategoryMapper;
import com.atguigu.gmall.pms.service.ProductCategoryService;
import com.atguigu.gmall.to.PmsProductCategoryWithChildrenItem;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.additional.query.impl.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 产品分类 服务实现类
 * </p>
 *
 * @author Lfy
 * @since 2019-03-19
 */
@Slf4j
@Component
@Service
public class ProductCategoryServiceImpl extends ServiceImpl<ProductCategoryMapper, ProductCategory> implements ProductCategoryService {


    @Autowired
    StringRedisTemplate redisTemplate;
    @Override
    public List<PmsProductCategoryWithChildrenItem> listWithChildren() {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        String cache = ops.get(RedisCacheConstant.PRODUCT_CATEGORY_CACHE_KEY);
        if(!StringUtils.isEmpty(cache)){
            log.info("PRODUCT_CATEGORY_CACHE 缓存命中....");
            //转化过来返回出去
            List<PmsProductCategoryWithChildrenItem> items = JSON.parseArray(cache, PmsProductCategoryWithChildrenItem.class);

            return items;
        }

        log.info("PRODUCT_CATEGORY_CACHE 缓存未命中，去查询数据库");
        ProductCategoryMapper baseMapper = getBaseMapper();
        List<PmsProductCategoryWithChildrenItem> items = baseMapper.listWithChildren(0);
        //存数据都给一个过期时间比较好；
        String jsonString = JSON.toJSONString(items);
        ops.set(RedisCacheConstant.PRODUCT_CATEGORY_CACHE_KEY,jsonString,3, TimeUnit.DAYS);
        //查某个菜单的所有子菜单
        //TODO 这个数据加缓存，
        return items;
    }

    @Override
    public Map<String, Object> pageList(Long parentId, Integer pageNum, Integer pageSize) {
        ProductCategoryMapper baseMapper = getBaseMapper();

        Page<ProductCategory> page = new Page<>(pageNum, pageSize);

        IPage<ProductCategory> selectPage = baseMapper.selectPage(page, null);

        //封装数据
        HashMap<String, Object> map = new HashMap<>();
        map.put("pageSize",pageSize);
        map.put("totalPage",selectPage.getPages());
        map.put("total",selectPage.getTotal());
        map.put("pageNum",selectPage.getCurrent());
        map.put("list",selectPage.getRecords());

        return map;
    }
}
