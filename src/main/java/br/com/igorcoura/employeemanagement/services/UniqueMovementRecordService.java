package br.com.igorcoura.employeemanagement.services;

import br.com.igorcoura.employeemanagement.Mapper.MovementRecordMapper;
import br.com.igorcoura.employeemanagement.domain.entities.MovementRecord;
import br.com.igorcoura.employeemanagement.domain.models.movementRecord.MovementRecordModel;
import br.com.igorcoura.employeemanagement.domain.models.movementRecord.NewUniqueMovementRecordModel;
import br.com.igorcoura.employeemanagement.repository.EmployeeRepository;
import br.com.igorcoura.employeemanagement.repository.MovementRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class UniqueMovementRecordService {

    @Autowired
    MovementRecordRepository movementRecordRepository;
    @Autowired
    EmployeeRepository employeeRepository;


    public MovementRecordModel insert(NewUniqueMovementRecordModel newUniqueMovementRecordModel){
        var employee = employeeRepository.getById(newUniqueMovementRecordModel.getIdEmployee());
        var openList = movementRecordRepository.findAll(Example.of(MovementRecord.builder().isOpen(true).employee(employee).build()));
        MovementRecord movement = null;
        if(openList.stream().count() > 0){
            movement = checkMovementValidity(openList, newUniqueMovementRecordModel);
            movement = addMovementExistingRecord(movement, newUniqueMovementRecordModel);
        }
        if(movement == null){
            movement = MovementRecord.builder()
                    .employee(employee)
                    .startTimeWork(newUniqueMovementRecordModel.getDate())
                    .isOpen(true).build();
        }
        return MovementRecordMapper.toModel(movementRecordRepository.save(movement));
    }

    private MovementRecord checkMovementValidity(List<MovementRecord> listMovementRecord, NewUniqueMovementRecordModel newUniqueMovementRecordModel){

        List<MovementRecord> listValidMovement = new ArrayList<MovementRecord>();

        for(MovementRecord movement: listMovementRecord){
            if(movement.getStartTimeWork() == null){
                movement.setOpen(false);
                movementRecordRepository.save(movement);
                continue;
            }

            if(movement.getEndTimeWork() != null){
                movement.setOpen(false);
                movementRecordRepository.save(movement);
                continue;
            }

            //Calculate date limit
            var dateLimit = movement.getStartTimeWork().plusDays(1);
            var startWork = movement.getEmployee().getStartWork().plusHours(-1);
            var dateTimeLimit = LocalDateTime.of(dateLimit.getYear(), dateLimit.getMonth(), dateLimit.getDayOfMonth(), startWork.getHour(), startWork.getMinute());

            //Check if the new movement is on the date limit
            //if it is not within the date limit, close the current movement.
            if(dateTimeLimit.isBefore(newUniqueMovementRecordModel.getDate()) || movement.getStartTimeWork().isAfter(newUniqueMovementRecordModel.getDate())){
                if(movement.getEndTimeWork() == null){
                    if(movement.getEndLunchTime() != null){
                        movement.setEndLunchTime(movement.getEndLunchTime());
                    }
                    else if(movement.getStartLunchTime() != null){
                        movement.setEndTimeWork(movement.getStartLunchTime());
                    }
                }
                movement.setOpen(false);
                movementRecordRepository.save(movement);
                continue;
            }
            listValidMovement.add(movement);
        }

        MovementRecord validMovement = null;
        boolean option1 = false, option2= false, option3= false;
        //if there is more than one valid case, this will choose the one with the most data filling in and close the rest.
        for(MovementRecord movement: listValidMovement){
            if(movement.getStartLunchTime() != null && !option1){
                if(movement.getEndLunchTime() != null){
                    option1 = true;
                }
                if(validMovement != null){
                    validMovement.setOpen(false);
                    movementRecordRepository.save(validMovement);
                }
                option2 = true;
                validMovement = movement;
            }

            if(movement.getEndLunchTime() != null && !option1 && !option2){
                if(validMovement != null){
                    validMovement.setOpen(false);
                    movementRecordRepository.save(validMovement);
                }
                option3 = true;
                validMovement = movement;
            }
            if(!option1 && !option2 && !option3){
                if(validMovement != null){
                    validMovement.setOpen(false);
                    movementRecordRepository.save(validMovement);
                }
                validMovement = movement;
            }
        }
        return validMovement;
    }

    private MovementRecord addMovementExistingRecord(MovementRecord movementRecord, NewUniqueMovementRecordModel newUniqueMovementRecordModel){
        if(movementRecord.getEndLunchTime() != null && movementRecord.getStartLunchTime() == null){
            movementRecord.setStartLunchTime(newUniqueMovementRecordModel.getDate());
            return movementRecord;
        }
        else if(movementRecord.getEndLunchTime() == null){
            movementRecord.setEndLunchTime(newUniqueMovementRecordModel.getDate());
            return movementRecord;
        }
        else if(movementRecord.getEndTimeWork() == null){
            movementRecord.setEndTimeWork(newUniqueMovementRecordModel.getDate());
            movementRecord.setOpen(false);
            return movementRecord;
        }
        return null;
    }

}