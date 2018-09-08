package com.wasteofplastic.multiworldmoney;

class Settings {

    private String newWorldMessage;
    private boolean showBalance;

    /**
     * @return the newWorldMessage
     */
    public String getNewWorldMessage() {
        return newWorldMessage;
    }
    /**
     * @return the showBalance
     */
    public boolean isShowBalance() {
        return showBalance;
    }
    /**
     * @param newWorldMessage the newWorldMessage to set
     */
    public void setNewWorldMessage(String newWorldMessage) {
        this.newWorldMessage = newWorldMessage;
    }
    /**
     * @param showBalance the showBalance to set
     */
    public void setShowBalance(boolean showBalance) {
        this.showBalance = showBalance;
    }

}
