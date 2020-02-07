package org.acme.mongodb.panache;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

class PersonDTO {
    public String id;
    public String name;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    public LocalDate birthDate;
    public Status status;


    public static final class Builder {
        public String id;
        public String name;
        public LocalDate birthDate;
        public Status status;

        private Builder() {
        }

        public static Builder aPersonDTO() {
            return new Builder();
        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withBirthDate(LocalDate birthDate) {
            this.birthDate = birthDate;
            return this;
        }

        public Builder withStatus(Status status) {
            this.status = status;
            return this;
        }

        public PersonDTO build() {
            PersonDTO personDTO = new PersonDTO();
            personDTO.id = this.id;
            personDTO.status = this.status;
            personDTO.name = this.name;
            personDTO.birthDate = this.birthDate;
            return personDTO;
        }
    }
}
