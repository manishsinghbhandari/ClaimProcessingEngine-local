package io.coredge.health.claims.mapper;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public class ClaimCallbackResponse {

        private String claimID;
        private String status;
        private String transitionDate;
        private List<Note> notes;
        private String message; // Only populated if claim not found in Payer system

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Note {
            private String note;
        }

}

