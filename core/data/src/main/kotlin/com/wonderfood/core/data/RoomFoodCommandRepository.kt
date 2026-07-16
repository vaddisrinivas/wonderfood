package com.wonderfood.core.data

import com.wonderfood.core.data.room.RoomFoodCommandRepository
import com.wonderfood.core.data.room.WonderFoodDatabase
import com.wonderfood.core.engine.FoodCommandRepository

public object FoodCommandRepositories {
    public fun room(database: WonderFoodDatabase): FoodCommandRepository =
        RoomFoodCommandRepository(database)
}
