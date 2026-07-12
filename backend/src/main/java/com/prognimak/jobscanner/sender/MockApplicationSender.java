package com.prognimak.jobscanner.sender;

import com.prognimak.jobscanner.entity.ApplicationDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockApplicationSender implements ApplicationSender {

    private static final Logger log = LoggerFactory.getLogger(MockApplicationSender.class);

    @Override
    public void send(ApplicationDraft draft) {
        log.info("Mock send for draft {}, job {} and CV {}", draft.getId(), draft.getJobOffer().getId(),
                draft.getCvFilePath());
    }
}
