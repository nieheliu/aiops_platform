package com.ops.ai.platform.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.Serializable;
import java.util.List;

public abstract class BaseController<T> {

    protected abstract IService<T> service();

    @PostMapping
    public Boolean create(@RequestBody T entity) {
        return service().save(entity);
    }

    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Serializable id) {
        return service().removeById(id);
    }

    @PutMapping
    public Boolean update(@RequestBody T entity) {
        return service().updateById(entity);
    }

    @GetMapping("/{id}")
    public T getById(@PathVariable Serializable id) {
        return service().getById(id);
    }

    @GetMapping
    public List<T> list() {
        return service().list();
    }

    @GetMapping("/page")
    public IPage<T> page(@RequestParam(defaultValue = "1") long current,
                         @RequestParam(defaultValue = "10") long size) {
        return service().page(Page.of(current, size));
    }
}
