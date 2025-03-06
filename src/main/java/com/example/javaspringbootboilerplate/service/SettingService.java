package com.example.javaspringbootboilerplate.service;

import com.example.javaspringbootboilerplate.entity.Setting;
import com.example.javaspringbootboilerplate.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettingService {
    private final SettingRepository settingRepository;

    public Setting create() {
        return settingRepository.save(Setting.builder().key("foo").value("bar").build());
    }
}
