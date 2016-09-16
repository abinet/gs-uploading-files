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
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

@Controller
public class FileUploadController {

    private final StorageService storageService;

    private List<String> logs   = new ArrayList ();

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

        model.addAttribute("logs", logs);
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
                logs.add( "received files " + file.length);
                LoggerFactory.getLogger(this.getClass()).info("received files " + file.length);
                for (int i = 0; i < file.length; i++) {
                    if(file[i].isEmpty()) continue;

                    String msg = "file " + file[i].getOriginalFilename() + " of type " + file[i].getContentType() + " received.";
                    logs.add(msg);
                    if(file[i].getContentType().equals("application/zip")) {
                        final ZipInputStream zipInputStream = new ZipInputStream(file[i].getInputStream());
                        ZipEntry entry;
                        while((entry = zipInputStream.getNextEntry()) !=null) {
                            if(!entry.isDirectory()) {
                                storageService.store(zipInputStream, entry);
                                msg = "file " + entry.getName() + " from zip was stored";
                                LoggerFactory.getLogger(this.getClass()).info(msg);
                                logs.add(msg);
                            }
                        }
                        zipInputStream.close();
                    } else {
                        storageService.store(file[i]);
                        msg = "file " + file[i].getOriginalFilename() + " was stored (" + (i+1) + " from " + file.length + ")";
                        LoggerFactory.getLogger(this.getClass()).info(msg);
                        logs.add(msg);
                    }
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
