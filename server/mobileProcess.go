package main

import . "../message"
import . "../blockData"
import "time"

func mobileProcessLoop(ctx *IContex) {
	ctx.mobile2deviceData.Init()
	ctx.mobile2deviceData.SendStep = DATA_DEVICE_SEND_STEP
	for true {
		select {
		case msg := <-main2mobileChan:
			onMsgReceived(ctx, *msg)

		default:
			mobileStep(ctx)
		}

	}
}

func mobileStep(ctx *IContex) {
	if DATA_STATE_SEND_REQUIRED == ctx.mobile2deviceData.State {
		data := ctx.mobile2deviceData.GetSendData()
		dataSend(ctx, DIVICE, data)
		ctx.mobile2deviceData.State = DATA_STATE_SENDING
	} else if DATA_STATE_SENDING == ctx.mobile2deviceData.State {

		if time.Now().Unix()-ctx.mobile2deviceData.SendTime > DATA_RETRY_TIMEOUT && ctx.mobile2deviceData.RetryCnt > 0 {
			data := ctx.mobile2deviceData.GetLastSendData()
			dataSend(ctx, DIVICE, data)
			ctx.mobile2deviceData.RetryCnt -= 1
		}
	}
}
