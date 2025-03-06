package com.example.javaspringbootboilerplate.dto.response.user;

import com.example.javaspringbootboilerplate.dto.response.PaginationResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public class UsersPaginationResponse extends PaginationResponse<UserResponse> {
    public UsersPaginationResponse(final Page<?> pageModel, final List<UserResponse> items) {
        super(pageModel, items);
    }
}
