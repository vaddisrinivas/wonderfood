package com.wonderfood.core.data

import com.wonderfood.core.data.room.RoomHouseholdRepository
import com.wonderfood.core.data.room.WonderFoodDatabase
import com.wonderfood.core.engine.HouseholdCommand
import com.wonderfood.core.engine.HouseholdCommandExecutionResult
import com.wonderfood.core.engine.HouseholdCommandRepository
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.Item

public interface HouseholdRepository : HouseholdCommandRepository {
    override suspend fun apply(command: HouseholdCommand): HouseholdCommandExecutionResult
    public suspend fun snapshot(householdId: HouseholdId): HouseholdSnapshot?
    public suspend fun searchItems(householdId: HouseholdId, query: String): List<Item>
}

public object HouseholdRepositories {
    public fun room(database: WonderFoodDatabase): HouseholdRepository =
        RoomHouseholdRepository(database)
}
