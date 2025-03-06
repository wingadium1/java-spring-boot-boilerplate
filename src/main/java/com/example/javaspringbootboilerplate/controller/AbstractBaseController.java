package com.example.javaspringbootboilerplate.controller;

import com.example.javaspringbootboilerplate.exception.BadRequestException;
import com.example.javaspringbootboilerplate.service.MessageSourceService;

import java.util.Arrays;

public abstract class AbstractBaseController {
    /**
     * Sort column check.
     *
     * @param messageSourceService MessageSourceService
     * @param sortColumns          String[]
     * @param sortBy               String
     */
    protected void sortColumnCheck(final MessageSourceService messageSourceService,
                                   final String[] sortColumns,
                                   final String sortBy) {
        if (sortBy != null && !Arrays.asList(sortColumns).contains(sortBy)) {
            throw new BadRequestException(messageSourceService.get("invalid_sort_column"));
        }
    }
}
