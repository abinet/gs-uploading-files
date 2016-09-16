package hello;

import hello.storage.StorageFileNotFoundException;
import hello.storage.StorageService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Controller
public class FileUploadController {

    private final StorageService storageService;

    private String status = "Ready";

    @Autowired
    public FileUploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/")
    public String listUploadedFiles(Model model) throws IOException {

        model.addAttribute("files", storageService
                .loadAll()
                .map(path ->
                        MvcUriComponentsBuilder
                                .fromMethodName(FileUploadController.class, "serveFile", path.getFileName().toString())
                                .build().toString())
                .collect(Collectors.toList()));

        return "uploadForm";
    }

    @GetMapping("/status")
    public String status(Model model)  {

        model.addAttribute("status", status);
        return "status";
    }


    @GetMapping("/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {

        Resource file = storageService.loadAsResource(filename);
        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""+file.getFilename()+"\"")
                .body(file);
    }

    @PostMapping("/")
    public Callable<String> handleFileUpload(@RequestParam("file") MultipartFile[] file,
                                             RedirectAttributes redirectAttributes) {
        return new Callable<String>() {
            @Override
            public String call() throws Exception {
                status = "received files " + file.length;
                LoggerFactory.getLogger(this.getClass()).info(status);
                for (int i = 0; i < file.length; i++) {
                    storageService.store(file[i]);
                    status = "file " + file[i].getOriginalFilename() + " was stored (" + (i+1) + " from " + file.length + ")";
                    LoggerFactory.getLogger(this.getClass()).info(status);
                }
                redirectAttributes.addFlashAttribute("message",
                        "You successfully uploaded " + file.length + " files!");

                return "redirect:/";
            }
        };
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }

}
