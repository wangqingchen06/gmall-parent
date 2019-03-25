package com.atguigu.gmall.pms.service;

import com.atguigu.gmall.pms.entity.ProductCategory;
import com.atguigu.gmall.to.PmsProductCategoryWithChildrenItem;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 产品分类 服务类
 * </p>
 *
 * @author Lfy
 * @since 2019-03-19
 */
public interface ProductCategoryService extends IService<ProductCategory> {
    //查询所有分类及子分类
    List<PmsProductCategoryWithChildrenItem> listWithChildren();
    //分页查询商品分类
    Map<String,Object> pageList(Long parentId, Integer pageNum, Integer pageSize);
}
