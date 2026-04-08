package com.smarthome.simulator.repo

import com.smarthome.simulator.entity.RoomConfigEntity
import org.springframework.data.jpa.repository.JpaRepository

interface RoomConfigRepository : JpaRepository<RoomConfigEntity, String>
