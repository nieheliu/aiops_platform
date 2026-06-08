package com.ops.ai.platform.service;

import com.ops.ai.platform.dto.LoginRequest;
import com.ops.ai.platform.dto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}
