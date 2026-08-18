package com.example.lowcode.template.application;

import java.util.List;

public record TemplatePage<T>(List<T> items, int page, int pageSize, long total) {
    public TemplatePage {
        items = List.copyOf(items);
    }
}
