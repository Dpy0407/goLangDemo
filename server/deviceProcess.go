package main

import (
	. "../blockData"
	. "../message"
	"time"
)

func deviceProcessLoop(ctx *IContex) {
	ctx.device2mobileData.Init()
	ctx.device2mobileData.SendStep = DATA_MOBILE_SEND_STEP
	for true {
		select {
		case msg := <-main2deviceChan:
			onMsgReceived(ctx, *msg)

		default:
			deviceStep(ctx)
		}
		time.Sleep(1 * time.Microsecond)
	}
}

func deviceStep(ctx *IContex) {
	baseStep(ctx, DEVICE)
}
