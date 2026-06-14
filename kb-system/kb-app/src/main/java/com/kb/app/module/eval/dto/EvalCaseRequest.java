package com.kb.app.module.eval.dto;

import lombok.Data;

@Data
public class EvalCaseRequest {

    private String question;

    private String groundTruth;
}
