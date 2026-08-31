package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.reporting.JasperReportService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class DocumentRestControllerTest {

    @Test
    void buildResponseShouldUseXlsxHeadersForExcelFormats() throws Exception {
        DocumentRestController controller = new DocumentRestController(mock(JdbcTemplate.class), mock(JasperReportService.class));
        byte[] payload = new byte[]{1, 2, 3};

        Method method = DocumentRestController.class.getDeclaredMethod("buildResponse", byte[].class, String.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        var response = (org.springframework.http.ResponseEntity<byte[]>) method.invoke(controller, payload, "XLSX", "REPORT_TEST");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), response.getHeaders().getContentType());
        assertEquals("attachment; filename=report-test.xlsx", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(payload, response.getBody());
    }
}


