package com.github.martvey.cli.entity.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@AllArgsConstructor
public class PTokenRequest {
    private String username;
    private String password;
}
