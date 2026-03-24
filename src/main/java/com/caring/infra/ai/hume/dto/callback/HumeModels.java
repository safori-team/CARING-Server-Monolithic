package com.caring.infra.ai.hume.dto.callback;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HumeModels {
    private HumeModelResult prosody;
    private HumeModelResult burst;
    private HumeModelResult language;
}
