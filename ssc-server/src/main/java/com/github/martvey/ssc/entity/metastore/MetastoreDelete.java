package com.github.martvey.ssc.entity.metastore;

import com.github.martvey.ssc.constant.MetastoreEnum;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class MetastoreDelete {
    private String id;
    private MetastoreEnum metastoreType;
}
