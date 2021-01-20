package com.bookstore.service;

import com.bookstore.entity.Author;
import com.bookstore.entity.Book;
import com.bookstore.repository.AuthorRepository;
import com.bookstore.repository.BookRepository;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookstoreService {

    private static final Logger log = Logger.getLogger(BookstoreService.class.getName());

    private final TransactionTemplate template;
    private final AuthorRepository authorRepository;
    private final BookRepository  bookRepository;

    public BookstoreService(AuthorRepository authorRepository, BookRepository  bookRepository,
            TransactionTemplate template) {
        this.authorRepository = authorRepository;
        this.bookRepository = bookRepository;
        this.template = template;
    }

    public void pessimisticWriteUpdate() throws InterruptedException {
        Thread tA = new Thread(() -> {
            template.setPropagationBehavior(
                    TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            
            template.execute(new TransactionCallbackWithoutResult() {

                @Override
                protected void doInTransactionWithoutResult(
                        TransactionStatus status) {

                    log.info("Starting first transaction (A) ...");

                    authorRepository.findById(1L).orElseThrow(); // get the lock
                    authorRepository.updateGenre("Comedy", 1L);

                    try {
                        log.info("Holding in place first transaction (A) for 10s ...");
                        Thread.sleep(10000);                        
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    
                    log.info("First transaction (A) attempts to update a book (id:1) ...");
                    bookRepository.findById(1L).orElseThrow(); // this cannot be done, transaction (B) holds the lock                    
                    bookRepository.updateTitle("A happy day", 1L); 
                }
            });

            log.info("First transaction (A) committed!"); 
        });

        Thread tB = new Thread(() -> {
            template.setPropagationBehavior(
                    TransactionDefinition.PROPAGATION_REQUIRES_NEW);            

            template.execute(new TransactionCallbackWithoutResult() {

                @Override
                protected void doInTransactionWithoutResult(
                        TransactionStatus status) {

                    log.info("Starting second transaction (B) ...");

                    bookRepository.findById(1L).orElseThrow(); // get the lock
                    bookRepository.updateTitle("A long night", 1L);        
                    
                    try {
                        log.info("Holding in place second transaction (B) for 10s ...");
                        Thread.sleep(10000);                        
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    
                    log.info("Second transaction (B) attempts to update an author (id:1) ...");
                    authorRepository.findById(1L).orElseThrow(); // this cannot be done, transaction (A) holds the lock
                    authorRepository.updateGenre("Horror", 1L); 
                }
            });

            log.info("Second transaction (B) committed!");
        });
        
        tA.start();
        Thread.sleep(5000);
        tB.start();
        
        tA.join();
        tB.join();
    }   
}