package dev.kairo.events;

import dev.kairo.core.KairoId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class EventDispatcherTest {
    @Test
    void dispatchesCaptureTargetBubbleAndHonorsStopPropagation() {
        KairoId root = KairoId.next("root");
        KairoId box = KairoId.next("box");
        KairoId btn = KairoId.next("btn");
        EventDispatcher dispatcher = new EventDispatcher();
        List<String> log = new ArrayList<>();

        dispatcher.addCaptureListener(root, CommonEvents.ClickEvent.class, e -> log.add("capture:root:" + e.phase()));
        dispatcher.addCaptureListener(box, CommonEvents.ClickEvent.class, e -> log.add("capture:box:" + e.phase()));
        dispatcher.addListener(btn, CommonEvents.ClickEvent.class, e -> log.add("target:btn:" + e.phase()));
        dispatcher.addListener(box, CommonEvents.ClickEvent.class, e -> log.add("bubble:box:" + e.phase()));
        dispatcher.addListener(root, CommonEvents.ClickEvent.class, e -> log.add("bubble:root:" + e.phase()));

        dispatcher.dispatch(new CommonEvents.ClickEvent(btn), List.of(root, box, btn));

        assertThat(log).containsExactly(
                "capture:root:CAPTURE",
                "capture:box:CAPTURE",
                "target:btn:TARGET",
                "bubble:box:BUBBLE",
                "bubble:root:BUBBLE"
        );

        log.clear();
        dispatcher.addCaptureListener(box, CommonEvents.ClickEvent.class, UIEvent::stopPropagation);
        CommonEvents.ClickEvent stopped = new CommonEvents.ClickEvent(btn);
        stopped.preventDefault();
        dispatcher.dispatch(stopped, List.of(root, box, btn));

        assertThat(log).containsExactly("capture:root:CAPTURE", "capture:box:CAPTURE");
        assertThat(stopped.isDefaultPrevented()).isTrue();
    }
}
