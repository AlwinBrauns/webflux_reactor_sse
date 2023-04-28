package com.example.sseplayground.controller;

import com.example.sseplayground.models.Message;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

@RestController
@CrossOrigin("*")
@RequestMapping("api/v1/")
@RequiredArgsConstructor
public class UploadController {
    private final SSEStreamController sseStreamController;
    private final String UPLOAD_DIRECTION = System.getProperty("user.dir") + "/uploads/";

    @PostConstruct
    private void createFolderIfNotThere() {
        Path uploadDirectory = Paths.get(UPLOAD_DIRECTION);
        if (!Files.exists(uploadDirectory)) {
            try {
                Files.createDirectories(uploadDirectory);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create upload directory", e);
            }
        }
    }

    private void saveFile(Path path, FilePart filePart) {
        Mono.delay(Duration.ofSeconds(1)).doOnNext(
                __ -> filePart.transferTo(path)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe()
        ).subscribe();
    }
    private Mono<Long> getFileSize(FilePart filePart) {
        return filePart.content().map(DataBuffer::readableByteCount).reduce(0L, Long::sum);
    }
    private Mono<Long> getStatus(Path path, FilePart filePart) {
        return getFileSize(filePart).flatMap(
                expectedSize -> {
                    return Mono.fromCallable(
                            () -> {
                                long _status = 0L;
                                if (Files.exists(path)) {
                                    return (Files.size(path) * 100) / expectedSize;
                                }
                                return _status;
                            }
                    ).subscribeOn(Schedulers.boundedElastic());
                }
        );
    }

    private Flux<Progress> getProgress(Mono<Long> status, FilePart file) {
        return status
            .repeat()
            .timeout(Duration.ofSeconds(30))
            .distinctUntilChanged()
            .takeUntil(_status -> _status.equals(100L))
            .map(_status -> {
                return _status.toString() + "%";
            })
            .map(percentage -> Progress.getInstance().setProgress(percentage))
            .doOnNext(progress -> {
                progress.emitOnMessageStream(file.filename() + ":", sseStreamController.getMainSinks());
            });
    }

    @PostMapping(path = "upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> uploadFile(@RequestPart("file") FilePart file) {
        Path path = Paths.get(UPLOAD_DIRECTION, file.filename());
        getProgress(getStatus(path, file), file).subscribe();
        saveFile(path, file);
        return ResponseEntity.ok().build();
    }

    @Data
    private static class Progress {
        private String progress;
        private boolean isFinished = true;
        public Progress setProgress(String progress) {
            this.progress = progress;
            if(progress.equals("100%")) {
                isFinished = true;
            }
            return this;
        }

        public void emitOnMessageStream(String prefix, Sinks.Many<ServerSentEvent<String>> sink) {
            sink.tryEmitNext((new Message(prefix + this.progress)).toSSE());
        }

        private static Progress INSTANCE;
        private Progress() {};
        public static Progress getInstance() {
            if(INSTANCE == null) {
                INSTANCE = new Progress();
            }
            return INSTANCE;
        }
    }
}
