package com.atguigu.gmall.pms.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.mapper.*;
import com.atguigu.gmall.pms.service.ProductService;
import com.atguigu.gmall.to.PmsProductParam;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>
 * 商品信息 服务实现类
 * </p>
 *
 * @author Lfy
 * @since 2019-03-19
 */
@Slf4j
@Service
@Component
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Autowired
    ProductMapper productMapper;

    @Autowired
    ProductLadderMapper productLadderMapper;

    @Autowired
    ProductFullReductionMapper productFullReductionMapper;

    @Autowired
    MemberPriceMapper memberPriceMapper;

    @Autowired
    ProductAttributeValueMapper productAttributeValueMapper;


    @Autowired
    ProductCategoryMapper productCategoryMapper;


    @Autowired
    SkuStockMapper skuStockMapper;

    Map<Thread,Product> map = new HashMap<>();

    //spring的所有组件全是单例，一定会出现线程安全问题
    //只要没有共享属性，一个要读，一个要改，就不会出现安全问题；
    //读写不同步导致
    // int i = 0;

    //ThreadLocal：
    ThreadLocal<Product> productThreadLocal = new ThreadLocal<Product>();

    @Override
    public Map<String, Object> pageProduct(Integer pageSize, Integer pageNum) {

        ProductMapper baseMapper = getBaseMapper();
        Page<Product> page = new Page<>(pageNum, pageSize);
        //去数据库分页查
        IPage<Product> selectPage = baseMapper.selectPage(page, null);
        //封装数据
        HashMap<String, Object> map = new HashMap<>();
        map.put("pageSize",pageSize);
        map.put("totalPage",selectPage.getPages());
        map.put("total",selectPage.getTotal());
        map.put("pageNum",selectPage.getCurrent());
        map.put("list",selectPage.getRecords());

        return map;
    }

    @Override
    public Product selectById(Long id) {
        ProductMapper baseMapper = getBaseMapper();
        Product product = baseMapper.selectById(id);

        return product;
    }

    @Override
    public void updateStatus(List<Long> ids, Integer publishStatus) {
        ProductMapper baseMapper = getBaseMapper();
        if(publishStatus!=0){
            for (Long id:ids) {
                Product product = baseMapper.selectById(id);
                product.setPublishStatus(0);
            }
        }else {
            for (Long id:ids) {
                Product product = baseMapper.selectById(id);
                product.setPublishStatus(1);
            }
        }



    }

    @Transactional(propagation = Propagation.REQUIRED)
    @Override
    public void create(PmsProductParam productParam) {
        //1、保存商品的基本信息 pms_product（将刚才保存的这个商品的自增id获取出来）【REQUIRED】
        ProductServiceImpl psProxy = (ProductServiceImpl) AopContext.currentProxy();

        //保存SPU和SKU【REQUIRES_NEW】
        psProxy.saveBaseProductInfo(productParam);

        //Require
        psProxy.saveProductLadder(productParam.getProductLadderList());//【REQUIRED_NEW】
        psProxy.saveProductFullReduction(productParam.getProductFullReductionList());
        psProxy.saveMemberPrice(productParam.getMemberPriceList());
        psProxy.saveProductAttributeValue(productParam.getProductAttributeValueList());
        psProxy.updateProductCategoryCount();

    }

    //1、保存商品的基本信息 pms_product（将刚才保存的这个商品的自增id获取出来）【REQUIRED】
    @Transactional(propagation = Propagation.REQUIRED)
    public Long saveProduct(PmsProductParam productParam) {

        Product product = new Product();
        BeanUtils.copyProperties(productParam,product);
        int insert = productMapper.insert(product);
        log.debug("插入商品：{}",product.getId());

        //商品信息共享到ThreadLocal
        productThreadLocal.set(product);
        //map.put(Thread.currentThread(),product);
        return  product.getId();
    }

    //3、保存商品阶梯价格 到 saveProductLadder【REQUIRES_NEW】
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveProductLadder(List<ProductLadder> list){

        Product product = productThreadLocal.get();
        //Product product1 = map.get(Thread.currentThread());
        //2、保存商品的阶梯价格 到 pms_product_ladder【REQUIRES_NEW】
        for (ProductLadder ladder : list) {
            ladder.setProductId(product.getId());
            productLadderMapper.insert(ladder);
            log.debug("插入ladder{}",ladder.getId());
        }

    }

    //3、保存商品满减价格 到 pms_product_full_reduction【REQUIRES_NEW】
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveProductFullReduction(List<ProductFullReduction> list){
        Product product = productThreadLocal.get();
        for (ProductFullReduction reduction : list) {
            reduction.setProductId(product.getId());
            productFullReductionMapper.insert(reduction);
        }
    }

    //4、保存商品的会员价格 到 pms_member_price【REQUIRES_NEW】{// int i=10/0}
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveMemberPrice(List<MemberPrice> memberPrices){
        Product product = productThreadLocal.get();
        for (MemberPrice memberPrice : memberPrices) {
            memberPrice.setProductId(product.getId());
            memberPriceMapper.insert(memberPrice);
        }
        //lambda

    }

    //5、保存Sku信息
    @Transactional(propagation = Propagation.REQUIRED)
    public void saveSkuInfo(List<SkuStock> skuStocks){
        Product product = productThreadLocal.get();
        //1）、线程安全的。遍历修改不安全
        AtomicReference<Integer> i = new AtomicReference<>(0);
        NumberFormat numberFormat = DecimalFormat.getNumberInstance();
        numberFormat.setMinimumIntegerDigits(2);
        numberFormat.setMaximumIntegerDigits(2);

        skuStocks.forEach(skuStock -> {

            skuStock.setProductId(product.getId());

            String format = numberFormat.format(i.get());

            String code = "K_"+product.getId()+"_"+format;
            skuStock.setSkuCode(code);

            i.set(i.get() + 1);

            skuStockMapper.insert(skuStock);
        });
    }

    //6、保存参数及自定义规格 到 pms_product_attribute_value（）【REQUIRES_NEW】
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public  void saveProductAttributeValue(List<ProductAttributeValue> productAttributeValues){
        Product product = productThreadLocal.get();
        productAttributeValues.forEach((pav)->{
            pav.setProductId(product.getId());
            productAttributeValueMapper.insert(pav);
        });
    }

    //7、更新商品分类数目 【REQUIRES_NEW】
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProductCategoryCount(){
        Product product = productThreadLocal.get();
        Long id = product.getProductCategoryId();


        productCategoryMapper.updateCountById(id);

    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveBaseProductInfo(PmsProductParam productParam){
        ProductServiceImpl psProxy = (ProductServiceImpl) AopContext.currentProxy();
        //Required
        psProxy.saveProduct(productParam);//【REQUIRES_NEW】
        //Required
        psProxy.saveSkuInfo(productParam.getSkuStockList());
    }


    @Override
    public void deleteStatus(List<Long> ids, Integer deleteStatus) {
        ProductMapper baseMapper = getBaseMapper();

    }


}
