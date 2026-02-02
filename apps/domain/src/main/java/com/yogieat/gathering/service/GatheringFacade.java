package com.yogieat.gathering.service;

import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.domain.command.GatheringCommand;
import com.yogieat.gathering.domain.result.GatheringResult;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GatheringFacade {

    private final GatheringService gatheringService;

    @Transactional
    public GatheringResult.Create createGathering(GatheringCommand.Create command) {
        Gathering gathering = gatheringService.create(command);
        return GatheringResult.Create.of(gathering);
    }
}
