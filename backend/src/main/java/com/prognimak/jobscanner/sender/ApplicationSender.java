package com.prognimak.jobscanner.sender;

import com.prognimak.jobscanner.entity.ApplicationDraft;

public interface ApplicationSender {

    void send(ApplicationDraft draft);
}
