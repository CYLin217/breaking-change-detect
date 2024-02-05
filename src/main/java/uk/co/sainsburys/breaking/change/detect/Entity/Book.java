package uk.co.sainsburys.breaking.change.detect.Entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

public record Book(@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id, String title, String author) {
}
